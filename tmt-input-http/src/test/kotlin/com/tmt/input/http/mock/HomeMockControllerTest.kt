package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class HomeMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val groupStore = InMemoryStore<MockGroup>(idPrefix = "group")
    private val membershipStore = MockMembershipStore()
    private val shareStore = MockReviewShareStore()
    private val favoriteStore = MockFavoriteStore()
    private val groupAssembler = GroupAssembler(saveStore, membershipStore, shareStore)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                HomeMockController(
                    groupStore,
                    saveStore,
                    placeStore,
                    membershipStore,
                    shareStore,
                    groupAssembler,
                    ReviewCardAssembler(placeStore, favoriteStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun seedGroup(
        name: String,
        ownerId: Long = 999,
        createdAt: Instant = Instant.parse("2026-08-10T00:00:00Z"),
    ): MockGroup =
        groupStore
            .create { id ->
                MockGroup(id, name, "$name 소개", null, null, "cat_cafe", listOf("region_seongdong"), ownerId, createdAt)
            }.also { membershipStore.join(it.groupId, ownerId, it.createdAt) }

    @Test
    fun `홈은 인증 필수다 (A §5-2)`() {
        mockMvc.perform(get("/v1/home")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/home/feed")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `가입 그룹이 없으면 추천 그룹 캐러셀을 채운다`() {
        seedGroup("성수 커피 탐험대")
        seedGroup("나는야 초밥왕")

        mockMvc
            .perform(get("/v1/home").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("미식가1"))
            .andExpect(jsonPath("$.myGroups").isEmpty)
            .andExpect(jsonPath("$.recommendedGroups.length()").value(2))
    }

    @Test
    fun `가입 그룹이 있으면 가입 오래된 순으로 내리고 추천은 비운다`() {
        val first = seedGroup("성수 커피 탐험대")
        val second = seedGroup("나는야 초밥왕")
        membershipStore.join(second.groupId, 1, Instant.parse("2026-08-11T00:00:00Z"))
        membershipStore.join(first.groupId, 1, Instant.parse("2026-08-12T00:00:00Z"))

        mockMvc
            .perform(get("/v1/home").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myGroups.length()").value(2))
            .andExpect(jsonPath("$.myGroups[0].groupId").value(second.groupId))
            .andExpect(jsonPath("$.myGroups[1].groupId").value(first.groupId))
            .andExpect(jsonPath("$.recommendedGroups").isEmpty)
    }

    @Test
    fun `피드는 가입 그룹의 공유 리뷰를 합치고 중복을 제거한다 (G19)`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        val g1 = seedGroup("성수 커피 탐험대")
        val g2 = seedGroup("나는야 초밥왕")
        membershipStore.join(g1.groupId, 1, Instant.now())
        membershipStore.join(g2.groupId, 1, Instant.now())

        // 같은 리뷰가 두 그룹에 공유돼 있다
        val shared = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_1")
        shareStore.add(g1.groupId, 999, shared.reviewId!!)
        shareStore.add(g2.groupId, 999, shared.reviewId!!)
        val only =
            MockFixtures.review(
                saveStore,
                place.placeId,
                ownerId = 999,
                reviewId = "review_2",
                createdAt = Instant.parse("2026-08-13T00:00:00Z"),
            )
        shareStore.add(g1.groupId, 999, only.reviewId!!)

        mockMvc
            .perform(get("/v1/home/feed").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].reviewId").value("review_2"))
            .andExpect(jsonPath("$.items[1].reviewId").value("review_1"))
    }

    @Test
    fun `가입 그룹이 없으면 피드는 빈 배열이다`() {
        mockMvc
            .perform(get("/v1/home/feed").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `가입하지 않은 그룹의 공유 리뷰는 피드에 나오지 않는다`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        val other = seedGroup("남의 그룹")
        val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_1")
        shareStore.add(other.groupId, 999, review.reviewId!!)

        mockMvc
            .perform(get("/v1/home/feed").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(jsonPath("$.items").isEmpty)
    }
}
