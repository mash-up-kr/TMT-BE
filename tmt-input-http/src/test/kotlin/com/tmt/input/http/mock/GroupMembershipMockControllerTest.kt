package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class GroupMembershipMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val groupStore = InMemoryStore<MockGroup>(idPrefix = "group")
    private val membershipStore = MockMembershipStore()
    private val shareStore = MockReviewShareStore()
    private val ticketLedger = MockTicketLedger()

    private val place = MockFixtures.place(placeStore, "델리스피자")
    private val group: MockGroup =
        groupStore
            .create { id ->
                MockGroup(
                    id,
                    "성수 커피 탐험대",
                    "한 줄",
                    null,
                    null,
                    "cat_cafe",
                    listOf("region_seongdong"),
                    999,
                    Instant.now(),
                )
            }.also { membershipStore.join(it.groupId, 999, it.createdAt) }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                GroupMembershipMockController(
                    groupStore,
                    saveStore,
                    placeStore,
                    membershipStore,
                    shareStore,
                    ticketLedger,
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice(), MockTicketExceptionAdvice())
            .build()

    private fun join(
        userId: Long = 1,
        body: String? = null,
        key: String = "join-1",
    ) = mockMvc.perform(
        post("/v1/groups/${group.groupId}/memberships")
            .header(UserIdArgumentResolver.HEADER, userId.toString())
            .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .apply { body?.let { content(it) } },
    )

    @Test
    fun `가입 팝업 — 티켓이 있으면 joinable이다`() {
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/join-preview").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.group.name").value("성수 커피 탐험대"))
            .andExpect(jsonPath("$.availableTicketCount").value(1))
            .andExpect(jsonPath("$.requiredTicketCount").value(1))
            .andExpect(jsonPath("$.joinable").value(true))
            .andExpect(jsonPath("$.blockedReason").doesNotExist())
    }

    @Test
    fun `가입 팝업 — 티켓이 없으면 TICKET_REQUIRED, 이미 가입이면 ALREADY_MEMBER`() {
        ticketLedger.tryConsume(1)
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/join-preview").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(jsonPath("$.joinable").value(false))
            .andExpect(jsonPath("$.blockedReason").value("TICKET_REQUIRED"))

        mockMvc
            .perform(get("/v1/groups/${group.groupId}/join-preview").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(jsonPath("$.blockedReason").value("ALREADY_MEMBER"))
    }

    @Test
    fun `가입하면 티켓 1장이 차감된다 (TX-3)`() {
        join()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.groupId").value(group.groupId))
            .andExpect(jsonPath("$.sharedReviewIds").isEmpty)
            .andExpect(jsonPath("$.ticket.consumedCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))

        assertEquals(true, membershipStore.isMember(group.groupId, 1))
    }

    @Test
    fun `sourceReviewId를 보내면 가입과 동시에 그 리뷰가 공유된다 (경로 2)`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_1")

        join(body = """{ "sourceReviewId": "review_1" }""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sharedReviewIds[0]").value("review_1"))

        assertEquals(setOf("review_1"), shareStore.userShares(group.groupId, 1))
    }

    @Test
    fun `타인의 리뷰를 sourceReviewId로 보내면 REVIEW_NOT_FOUND다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 7, reviewId = "review_1")

        join(body = """{ "sourceReviewId": "review_1" }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `이미 가입 판정이 티켓 부족보다 먼저다 (G8)`() {
        ticketLedger.tryConsume(999) // 그룹장 티켓 소진
        join(userId = 999)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_GROUP_MEMBER"))
    }

    @Test
    fun `티켓이 없으면 409와 티켓 상태를 함께 내린다`() {
        ticketLedger.tryConsume(1)

        join()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GROUP_JOIN_TICKET_REQUIRED"))
            .andExpect(jsonPath("$.title").value("그룹 가입에 필요한 티켓이 부족합니다."))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))
            .andExpect(jsonPath("$.ticket.shortageCount").value(1))
    }

    @Test
    fun `공유 목록은 내 리뷰 전체를 공유 여부와 함께 내린다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_1")
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_2")
        join().andExpect(status().isCreated)
        shareStore.replace(group.groupId, 1, listOf("review_1"))

        mockMvc
            .perform(get("/v1/groups/${group.groupId}/review-shares").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.sharedCount").value(1))
            .andExpect(jsonPath("$.items[?(@.reviewId == 'review_1')].isShared").value(true))
            .andExpect(jsonPath("$.items[0].placeName").value("델리스피자"))
    }

    @Test
    fun `공유는 전체 교체다 — 빠진 것은 해제된다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_1")
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_2")
        join().andExpect(status().isCreated)
        shareStore.replace(group.groupId, 1, listOf("review_1"))

        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}/review-shares")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "reviewIds": ["review_2"] }"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sharedReviewIds[0]").value("review_2"))
            .andExpect(jsonPath("$.sharedCount").value(1))

        assertEquals(setOf("review_2"), shareStore.userShares(group.groupId, 1))
    }

    @Test
    fun `가입하지 않은 그룹에는 공유할 수 없다`() {
        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}/review-shares")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "reviewIds": [] }"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("GROUP_MEMBERSHIP_REQUIRED"))
    }

    @Test
    fun `탈퇴하면 공유했던 리뷰가 전부 내려간다 (G10)`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_1")
        join(body = """{ "sourceReviewId": "review_1" }""").andExpect(status().isCreated)

        mockMvc
            .perform(delete("/v1/groups/${group.groupId}/memberships/me").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNoContent)

        assertEquals(false, membershipStore.isMember(group.groupId, 1))
        assertEquals(emptySet<String>(), shareStore.userShares(group.groupId, 1))
        // 티켓은 돌아오지 않는다 (T9)
        assertEquals(0, ticketLedger.availableCount(1))
    }

    @Test
    fun `그룹장은 탈퇴할 수 없다 (G11)`() {
        mockMvc
            .perform(delete("/v1/groups/${group.groupId}/memberships/me").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("GROUP_OWNER_CANNOT_LEAVE"))
    }
}
