package com.tmt.input.http.controller

import com.tmt.application.port.input.GetHomeFeedUseCase
import com.tmt.application.port.input.GetHomeUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.HomeFeedRequest
import com.tmt.application.port.input.HomeFeedResult
import com.tmt.application.port.input.HomeResult
import com.tmt.application.port.input.ReviewCardView
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/**
 * 홈 실구현의 어댑터 계약 — 응답 형태·ID 표기·커서 왕복·인증 필수가 mock과 같은지 지킨다.
 */
class HomeControllerTest {
    private var homeResult = HomeResult(nickname = "하아얀", myGroups = emptyList(), recommendedGroups = emptyList())
    private var feedResult = HomeFeedResult(items = emptyList(), hasNext = false, sortedByDistance = true)
    private var lastFeedRequest: HomeFeedRequest? = null

    private val homeUseCase =
        object : GetHomeUseCase {
            override fun get(viewerId: Long) = homeResult
        }

    private val feedUseCase =
        object : GetHomeFeedUseCase {
            override fun get(request: HomeFeedRequest): HomeFeedResult {
                lastFeedRequest = request
                return feedResult
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(HomeController(homeUseCase, feedUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `가입 그룹이 0개여도 홈이 성립하고 추천 그룹은 채워진다`() {
        homeResult = HomeResult(nickname = "하아얀", myGroups = emptyList(), recommendedGroups = listOf(groupCard(3)))

        mockMvc
            .perform(get("/v1/home").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("하아얀"))
            .andExpect(jsonPath("$.myGroups").isEmpty)
            .andExpect(jsonPath("$.recommendedGroups[0].groupId").value("group_3"))
            .andExpect(jsonPath("$.recommendedGroups[0].matchedSavedPlaceCount").value(7))
    }

    @Test
    fun `내 그룹과 추천 그룹이 mock과 같은 group_ 접두로 나간다`() {
        homeResult =
            HomeResult(
                nickname = "하아얀",
                myGroups = listOf(HomeResult.MyGroup(1, "성수 커피 탐험대", "https://cdn.example/a.jpg")),
                recommendedGroups = listOf(groupCard(3)),
            )

        mockMvc
            .perform(get("/v1/home").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myGroups[0].groupId").value("group_1"))
            .andExpect(jsonPath("$.myGroups[0].imageUrl").value("https://cdn.example/a.jpg"))
            .andExpect(jsonPath("$.recommendedGroups[0].groupId").value("group_3"))
    }

    @Test
    fun `비로그인은 401이다 — 홈은 인증 필수다`() {
        mockMvc
            .perform(get("/v1/home"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))

        mockMvc
            .perform(get("/v1/home/feed"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `피드 카드가 mock과 같은 접두 ID 표기로 나간다`() {
        feedResult = HomeFeedResult(items = listOf(reviewCard()), hasNext = false, sortedByDistance = true)

        mockMvc
            .perform(
                get("/v1/home/feed?latitude=37.4857&longitude=126.8887").header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_7"))
            .andExpect(jsonPath("$.items[0].author.userId").value("user_901"))
            .andExpect(jsonPath("$.items[0].place.placeId").value("place_5"))
            .andExpect(jsonPath("$.items[0].photos[0].photoId").value("sp_13"))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `거리 정렬 커서가 마지막 행의 키를 그대로 되읽는다`() {
        feedResult = HomeFeedResult(items = listOf(reviewCard()), hasNext = true, sortedByDistance = true)
        val cursor = nextCursorOf("/v1/home/feed?latitude=37.4857&longitude=126.8887")

        mockMvc
            .perform(
                get("/v1/home/feed?latitude=37.4857&longitude=126.8887&cursor=$cursor")
                    .header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isOk)

        // 다음 페이지는 마지막 행 다음부터 — 경계에서 중복·누락이 없다
        assertEquals(120, lastFeedRequest?.after?.distanceMeters)
        assertEquals(7L, lastFeedRequest?.after?.reviewId)
    }

    @Test
    fun `좌표가 없으면 최신순이고 커서에 createdAt이 실린다`() {
        feedResult = HomeFeedResult(items = listOf(reviewCard()), hasNext = true, sortedByDistance = false)
        val cursor = nextCursorOf("/v1/home/feed")

        mockMvc
            .perform(get("/v1/home/feed?cursor=$cursor").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)

        assertEquals(Instant.parse("2026-08-20T00:00:00Z"), lastFeedRequest?.after?.createdAt)
        assertEquals(7L, lastFeedRequest?.after?.reviewId)
        assertNull(lastFeedRequest?.after?.distanceMeters)
    }

    @Test
    fun `좌표가 바뀌거나 붙으면 이전 커서는 무효다`() {
        feedResult = HomeFeedResult(items = listOf(reviewCard()), hasNext = true, sortedByDistance = true)
        val cursor = nextCursorOf("/v1/home/feed?latitude=37.4857&longitude=126.8887")

        mockMvc
            .perform(
                get("/v1/home/feed?latitude=37.5000&longitude=126.9000&cursor=$cursor")
                    .header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))

        // 정렬 자체가 갈리므로 좌표를 빼고 이어붙이는 것도 막는다
        mockMvc
            .perform(get("/v1/home/feed?cursor=$cursor").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private fun nextCursorOf(url: String): String =
        mockMvc
            .perform(get(url).header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasNext").value(true))
            .andReturn()
            .response
            .contentAsString
            .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun groupCard(id: Long) =
        GroupCardView(
            groupId = id,
            name = "성수 커피 탐험대",
            oneLineDescription = "커피 좋아하는 사람 모여라",
            coverImageUrl = "https://cdn.example/cover.jpg",
            memberCount = 12,
            reviewCount = 30,
            placeCount = 11,
            matchedSavedPlaceCount = 7,
        )

    private fun reviewCard() =
        ReviewCardView(
            reviewId = 7,
            authorId = 901,
            authorNickname = "미식가",
            authorProfileImageUrl = null,
            rating = 5,
            distanceMeters = 120,
            photos = listOf(ReviewCardView.Photo(photoId = 13, url = "https://example.com/1.jpg", order = 0)),
            aiSummary = null,
            content = "맛있었어요",
            tags = listOf(ReviewCardView.Tag("tag_tasty", "음식이 맛있어요")),
            placeId = 5,
            placeName = "큰집",
            placeRegionName = "구로구 구로동",
            placeCategoryId = "cat_meat",
            placeCategoryName = "고기·구이",
            placeFavorite = false,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        )
}
