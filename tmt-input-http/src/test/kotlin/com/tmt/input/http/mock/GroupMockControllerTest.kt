package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class GroupMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val groupStore = InMemoryStore<MockGroup>(idPrefix = "group")
    private val membershipStore = MockMembershipStore()
    private val shareStore = MockReviewShareStore()
    private val favoriteStore = MockFavoriteStore()
    private val userStore = MockUserStore(listOf(MockUser(1, "조용한 미식가", "tester1@example.com")))
    private val aiSummaryStore = MockAiSummaryStore()
    private val groupAssembler = GroupAssembler(fakeMockMediaUrls(), saveStore, membershipStore, shareStore)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                GroupMockController(
                    groupStore,
                    membershipStore,
                    groupAssembler,
                    ReviewCardAssembler(fakeMockMediaUrls(), placeStore, favoriteStore, aiSummaryStore, userStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun seedGroup(
        name: String = "성수 커피 탐험대",
        ownerId: Long = 999,
        oneLine: String = "조용히 커피 맛에 집중하는 사람들",
        foodCategoryId: String = "cat_cafe",
    ): MockGroup {
        val group =
            groupStore.create { id ->
                MockGroup(
                    id,
                    name,
                    oneLine,
                    null,
                    null,
                    foodCategoryId,
                    listOf("region_seongdong"),
                    ownerId,
                    Instant.now(),
                )
            }
        membershipStore.join(group.groupId, ownerId, group.createdAt)
        return group
    }

    private val createBody =
        """
        {
          "name": "나는야 초밥왕",
          "oneLineDescription": "회전 초밥부터 오마카세까지",
          "foodCategoryId": "cat_japanese",
          "regionTagIds": ["region_seoul_all"]
        }
        """.trimIndent()

    @Test
    fun `그룹 리뷰 목록은 미가입이어도 전체를 페이징한다 (G1)`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        (1..5).forEach { i ->
            val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_$i")
            shareStore.add(group.groupId, 999, review.reviewId!!)
        }

        // 미가입 (user 1) — 개수 제한이 없고 커서도 회원과 같게 채운다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews?limit=3").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.gate.gated").value(true))
            .andExpect(jsonPath("$.gate.reason").value("MEMBERSHIP_REQUIRED"))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty)

        // 가입자 (owner 999)
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(jsonPath("$.items.length()").value(5))
            .andExpect(jsonPath("$.gate.gated").value(false))
            .andExpect(jsonPath("$.gate.reason").value(nullValue()))
    }

    @Test
    fun `미가입 응답은 본문과 단점 요약을 서버에서 지운다 (G1)`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_1")
        shareStore.add(group.groupId, 999, review.reviewId!!)
        aiSummaryStore.put(review.reviewId!!, pros = "분위기가 좋아요", cons = "웨이팅이 길어요")

        // 미가입 — content는 null이고 길이만 남는다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].content").value(nullValue()))
            .andExpect(jsonPath("$.items[0].contentLength").value(4))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value(nullValue()))
            .andExpect(jsonPath("$.items[0].aiSummary.pros").value("분위기가 좋아요"))
            .andExpect(jsonPath("$.items[0].rating").value(5))
            .andExpect(jsonPath("$.items[0].photos.length()").value(1))

        // 가입자 — 그대로 보이고 contentLength는 같은 값이다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(jsonPath("$.items[0].content").value("맛있어요"))
            .andExpect(jsonPath("$.items[0].contentLength").value(4))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value("웨이팅이 길어요"))
    }

    @Test
    fun `contentLength는 코드 포인트로 센다 — 이모지가 있어도 FE가 세는 값과 같다`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        val review =
            saveStore.create { id ->
                MockSave(
                    saveId = id,
                    ownerId = 999,
                    placeId = place.placeId,
                    photoAssetIds = listOf("asset_1"),
                    companionTagIds = emptyList(),
                    positivePointTagIds = emptyList(),
                    rating = 5,
                    // 이모지는 하나가 UTF-16으로 2 — 코드 포인트로 세면 7, UTF-16으로 세면 9다
                    content = "맛있어요 🍕🍕",
                    reviewId = "review_1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            }
        shareStore.add(group.groupId, 999, review.reviewId!!)

        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].contentLength").value(7))
    }

    @Test
    fun `없는 그룹은 GROUP_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/groups/group_999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }
}
