package com.tmt.input.http.controller

import com.tmt.application.port.input.GetPlaceDetailUseCase
import com.tmt.application.port.input.GetPlaceReviewsUseCase
import com.tmt.application.port.input.PlaceDetailView
import com.tmt.application.port.input.PlaceFavoriteUseCase
import com.tmt.application.port.input.PlaceReviewsRequest
import com.tmt.application.port.input.PlaceReviewsResult
import com.tmt.application.port.input.ReviewCardView
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/**
 * 가게 상세 실구현의 어댑터 계약 — 응답 형태·ID 표기·찜 멱등이 mock과 같은지 지킨다.
 * UT2 동안 이 컨트롤러는 `tmt.mock.place-explore=true`로 내려가 있다 (TMT-250).
 */
class PlaceDetailControllerTest {
    private val favorites = mutableSetOf<Pair<Long, Long>>()
    private var lastReviewsRequest: PlaceReviewsRequest? = null
    private var reviewsResult = PlaceReviewsResult(items = emptyList(), hasNext = false)

    private val detailUseCase =
        object : GetPlaceDetailUseCase {
            override fun get(
                viewerId: Long?,
                placeId: Long,
            ): PlaceDetailView {
                if (placeId != 5L) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
                return PlaceDetailView(
                    placeId = 5,
                    name = "큰집",
                    categoryName = "고기·구이",
                    averageRating = 4.5,
                    reviewCount = 2,
                    photos = listOf(PlaceDetailView.PlacePhoto(url = "https://example.com/1.jpg", reviewId = 7)),
                    roadAddress = "서울 구로구 도림로10길 23",
                    latitude = 37.4857,
                    longitude = 126.8887,
                    phoneNumber = null,
                    isFavorite = viewerId != null && (viewerId to 5L) in favorites,
                )
            }
        }

    private val reviewsUseCase =
        object : GetPlaceReviewsUseCase {
            override fun get(request: PlaceReviewsRequest): PlaceReviewsResult {
                lastReviewsRequest = request
                return reviewsResult
            }
        }

    private val favoriteUseCase =
        object : PlaceFavoriteUseCase {
            override fun add(
                userId: Long,
                placeId: Long,
            ) {
                favorites += userId to placeId
            }

            override fun remove(
                userId: Long,
                placeId: Long,
            ) {
                favorites -= userId to placeId
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(PlaceDetailController(detailUseCase, reviewsUseCase, favoriteUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `상세가 mock과 같은 필드로 나간다`() {
        mockMvc
            .perform(get("/v1/places/place_5").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.placeId").value("place_5"))
            .andExpect(jsonPath("$.name").value("큰집"))
            .andExpect(jsonPath("$.averageRating").value(4.5))
            .andExpect(jsonPath("$.reviewCount").value(2))
            .andExpect(jsonPath("$.photos[0].reviewId").value("rv_7"))
            .andExpect(jsonPath("$.isFavorite").value(false))
    }

    @Test
    fun `비로그인도 상세를 볼 수 있고 찜은 false다`() {
        mockMvc
            .perform(get("/v1/places/place_5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isFavorite").value(false))
    }

    @Test
    fun `접두가 어긋난 placeId는 PLACE_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/places/place_abc").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }

    @Test
    fun `찜은 두 번 눌러도 200이다 (F2)`() {
        repeat(2) {
            mockMvc
                .perform(put("/v1/places/place_5/favorite").header(UserIdArgumentResolver.HEADER, "1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isFavorite").value(true))
        }
        mockMvc
            .perform(get("/v1/places/place_5").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(jsonPath("$.isFavorite").value(true))

        repeat(2) {
            mockMvc
                .perform(delete("/v1/places/place_5/favorite").header(UserIdArgumentResolver.HEADER, "1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isFavorite").value(false))
        }
    }

    @Test
    fun `찜에는 인증이 필요하다`() {
        mockMvc
            .perform(put("/v1/places/place_5/favorite"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `리뷰 목록 커서가 같은 매장에서만 유효하다`() {
        reviewsResult = PlaceReviewsResult(items = listOf(reviewCard()), hasNext = true)

        val cursor =
            mockMvc
                .perform(get("/v1/places/place_5/reviews").header(UserIdArgumentResolver.HEADER, "1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].reviewId").value("rv_7"))
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(get("/v1/places/place_5/reviews?cursor=$cursor").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
        assertEquals(7L, lastReviewsRequest?.after?.reviewId)

        mockMvc
            .perform(get("/v1/places/place_9/reviews?cursor=$cursor").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `좌표를 주면 그대로 유스케이스에 전달된다`() {
        mockMvc
            .perform(
                get("/v1/places/place_5/reviews?latitude=37.4857&longitude=126.8887")
                    .header(UserIdArgumentResolver.HEADER, "1"),
            ).andExpect(status().isOk)

        assertEquals(37.4857, lastReviewsRequest?.viewerLatitude)
        assertEquals(126.8887, lastReviewsRequest?.viewerLongitude)
    }

    private fun reviewCard() =
        ReviewCardView(
            reviewId = 7,
            authorId = 901,
            authorNickname = "미식가",
            authorProfileImageUrl = null,
            rating = 5,
            distanceMeters = null,
            photos = emptyList(),
            aiSummary = null,
            content = "맛있었어요",
            tags = emptyList(),
            placeId = 5,
            placeName = "큰집",
            placeRegionName = "구로구 구로동",
            placeCategoryName = "고기·구이",
            placeFavorite = false,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        )
}
