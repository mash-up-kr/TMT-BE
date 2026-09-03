package com.tmt.input.http.controller

import com.tmt.application.port.input.GetNearbyPlacesUseCase
import com.tmt.application.port.input.GetNearbyReviewsUseCase
import com.tmt.application.port.input.NearbyPlacesRequest
import com.tmt.application.port.input.NearbyPlacesResult
import com.tmt.application.port.input.NearbyReviewsRequest
import com.tmt.application.port.input.NearbyReviewsResult
import com.tmt.application.port.input.ReviewCardView
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/**
 * 근처 탐색 실구현의 어댑터 계약 — 응답 형태·ID 표기·커서 왕복·필수 파라미터가 mock과 같은지 지킨다.
 */
class NearbyControllerTest {
    private var lastReviewsRequest: NearbyReviewsRequest? = null
    private var lastPlacesRequest: NearbyPlacesRequest? = null

    private var reviewsResult = NearbyReviewsResult(items = emptyList(), hasNext = false)
    private var placesResult = NearbyPlacesResult(pins = emptyList(), truncated = false)

    private val reviewsUseCase =
        object : GetNearbyReviewsUseCase {
            override fun get(request: NearbyReviewsRequest): NearbyReviewsResult {
                lastReviewsRequest = request
                return reviewsResult
            }
        }

    private val placesUseCase =
        object : GetNearbyPlacesUseCase {
            override fun get(request: NearbyPlacesRequest): NearbyPlacesResult {
                lastPlacesRequest = request
                return placesResult
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(NearbyController(reviewsUseCase, placesUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `리뷰 카드가 mock과 같은 접두 ID 표기로 나간다`() {
        reviewsResult = NearbyReviewsResult(items = listOf(reviewCard()), hasNext = false)

        mockMvc
            .perform(
                get(
                    "/v1/nearby/reviews?latitude=37.4857&longitude=126.8887",
                ).header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_7"))
            .andExpect(jsonPath("$.items[0].author.userId").value("user_901"))
            .andExpect(jsonPath("$.items[0].place.placeId").value("place_5"))
            .andExpect(jsonPath("$.items[0].photos[0].photoId").value("sp_13"))
            .andExpect(jsonPath("$.items[0].place.categoryId").value("cat_meat"))
            .andExpect(jsonPath("$.items[0].place.categoryName").value("고기·구이"))
            .andExpect(jsonPath("$.items[0].contentLength").value(5))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `좌표가 없으면 400이다`() {
        mockMvc
            .perform(get("/v1/nearby/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `다음 페이지가 있으면 커서를 발급하고 같은 좌표에서 되읽는다`() {
        reviewsResult = NearbyReviewsResult(items = listOf(reviewCard()), hasNext = true)

        val cursor =
            mockMvc
                .perform(
                    get(
                        "/v1/nearby/reviews?latitude=37.4857&longitude=126.8887",
                    ).header(UserIdArgumentResolver.HEADER, "1"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(
                get("/v1/nearby/reviews?latitude=37.4857&longitude=126.8887&cursor=$cursor")
                    .header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isOk)

        assertEquals(120, lastReviewsRequest?.after?.distanceMeters)
        assertEquals(7L, lastReviewsRequest?.after?.reviewId)
    }

    @Test
    fun `좌표가 바뀌면 이전 커서는 무효다`() {
        reviewsResult = NearbyReviewsResult(items = listOf(reviewCard()), hasNext = true)
        val cursor =
            mockMvc
                .perform(
                    get(
                        "/v1/nearby/reviews?latitude=37.4857&longitude=126.8887",
                    ).header(UserIdArgumentResolver.HEADER, "1"),
                ).andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(
                get("/v1/nearby/reviews?latitude=37.5000&longitude=126.9000&cursor=$cursor")
                    .header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `지도 핀은 viewport가 필수이고 커서를 쓰지 않는다`() {
        mockMvc
            .perform(get("/v1/nearby/places?latitude=37.4857&longitude=126.8887"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        placesResult =
            NearbyPlacesResult(
                pins = listOf(NearbyPlacesResult.Pin(5, "큰집", 37.4857, 126.8887, "cat_korean", 3)),
                truncated = true,
            )

        mockMvc
            .perform(get("/v1/nearby/places?north=37.50&south=37.47&east=126.91&west=126.87&query=큰집"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].placeId").value("place_5"))
            .andExpect(jsonPath("$.items[0].categoryId").value("cat_korean"))
            .andExpect(jsonPath("$.items[0].reviewCount").value(3))
            .andExpect(jsonPath("$.truncated").value(true))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())

        assertEquals("큰집", lastPlacesRequest?.query)
        assertNotNull(lastPlacesRequest?.north)
    }

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
