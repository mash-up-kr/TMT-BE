package com.tmt.input.http.controller

import com.tmt.application.port.input.GetReviewedPlacesUseCase
import com.tmt.application.port.input.PlaceRecommendationCommand
import com.tmt.application.port.input.PlaceRecommendationView
import com.tmt.application.port.input.RecommendPlaceUseCase
import com.tmt.application.port.input.ReviewedPlaceKey
import com.tmt.application.port.input.ReviewedPlaceSlice
import com.tmt.application.port.input.ReviewedPlaceView
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 매장 추천 어댑터 계약 (TMT-289) — ID 표기·커서 발급·요청 파싱이 명세 J §5와 맞는지 본다.
 * 추천 규칙 자체는 [com.tmt.application.domain.recommendation] 쪽 테스트가 덮는다.
 */
class RecommendationControllerTest {
    private var slice = ReviewedPlaceSlice(items = emptyList(), hasNext = false)
    private var lastAfter: ReviewedPlaceKey? = null
    private var lastCommand: PlaceRecommendationCommand? = null
    private var recommendation = view()
    private var failure: TmtException? = null

    private val getReviewedPlaces =
        object : GetReviewedPlacesUseCase {
            override fun list(
                userId: Long,
                after: ReviewedPlaceKey?,
                limit: Int,
            ): ReviewedPlaceSlice {
                lastAfter = after
                return slice
            }
        }

    private val recommendPlace =
        object : RecommendPlaceUseCase {
            override fun recommend(command: PlaceRecommendationCommand): PlaceRecommendationView {
                lastCommand = command
                failure?.let { throw it }
                return recommendation
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(RecommendationController(getReviewedPlaces, recommendPlace))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    // --- 격자 (§5-1) ---

    @Test
    fun `격자 항목이 접두 ID 표기로 나간다`() {
        slice = ReviewedPlaceSlice(items = listOf(item(placeId = 9)), hasNext = false)

        mockMvc
            .perform(get("/v1/users/me/reviewed-places").requestAttr(USER_ID, 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].placeId").value("place_9"))
            .andExpect(jsonPath("$.items[0].name").value("온화커피"))
            .andExpect(jsonPath("$.items[0].categoryId").value("cat_cafe"))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `격자는 categoryName을 내리지 않는다`() {
        // 칸에 그리는 것은 이미지와 이름뿐이라 카테고리 라벨을 표시할 자리가 없다 (§5-1)
        slice = ReviewedPlaceSlice(items = listOf(item(placeId = 9)), hasNext = false)

        mockMvc
            .perform(get("/v1/users/me/reviewed-places").requestAttr(USER_ID, 1L))
            .andExpect(jsonPath("$.items[0].categoryName").doesNotExist())
    }

    @Test
    fun `사진 0장 매장은 thumbnailUrl이 null로 나간다`() {
        slice = ReviewedPlaceSlice(items = listOf(item(placeId = 9, thumbnailUrl = null)), hasNext = false)

        mockMvc
            .perform(get("/v1/users/me/reviewed-places").requestAttr(USER_ID, 1L))
            .andExpect(jsonPath("$.items[0].thumbnailUrl").doesNotExist())
    }

    @Test
    fun `다음 페이지가 있으면 커서가 나가고 그대로 돌아온다`() {
        slice = ReviewedPlaceSlice(items = listOf(item(placeId = 9)), hasNext = true)

        val cursor =
            mockMvc
                .perform(get("/v1/users/me/reviewed-places").requestAttr(USER_ID, 1L))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(
                get("/v1/users/me/reviewed-places")
                    .requestAttr(USER_ID, 1L)
                    .param("cursor", cursor),
            ).andExpect(status().isOk)

        assertEquals(ReviewedPlaceKey(REVIEWED_AT, 9L), lastAfter)
    }

    @Test
    fun `다른 사용자의 커서는 INVALID_CURSOR다`() {
        // 커서 조건에 userId가 들어가 남의 커서로는 열리지 않는다
        slice = ReviewedPlaceSlice(items = listOf(item(placeId = 9)), hasNext = true)
        val cursor =
            mockMvc
                .perform(get("/v1/users/me/reviewed-places").requestAttr(USER_ID, 1L))
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(
                get("/v1/users/me/reviewed-places")
                    .requestAttr(USER_ID, 2L)
                    .param("cursor", cursor),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `격자는 인증이 필요하다`() {
        mockMvc
            .perform(get("/v1/users/me/reviewed-places"))
            .andExpect(status().isUnauthorized)
    }

    // --- 추천 (§5-2) ---

    @Test
    fun `고른 매장 표기가 숫자 id로 풀려 유스케이스에 넘어간다`() {
        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 7L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isOk)

        assertEquals(listOf(1L, 9L), lastCommand?.seedPlaceIds)
        assertEquals(7L, lastCommand?.userId)
    }

    @Test
    fun `결과 카드가 접두 ID 표기로 나간다`() {
        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.place.placeId").value("place_31"))
            .andExpect(jsonPath("$.place.categoryName").value("양식"))
            .andExpect(jsonPath("$.summary.reviewId").value("rv_77"))
            .andExpect(jsonPath("$.summary.pros").value("분위기가 좋아요"))
    }

    @Test
    fun `요약이 없으면 summary가 null로 나간다`() {
        recommendation = view().copy(summary = null)

        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.summary").doesNotExist())
    }

    @Test
    fun `매장 표기가 아닌 값은 PLACE_NOT_FOUND다`() {
        // 접두·형식이 어긋나면 없는 자원과 같다 (PublicIds)
        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","group_9"]}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }

    @Test
    fun `바디가 없으면 400이다`() {
        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `후보가 없으면 422로 나간다`() {
        failure = TmtException(ErrorCode.RECOMMENDATION_UNAVAILABLE)

        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_UNAVAILABLE"))
    }

    @Test
    fun `LLM 실패는 503으로 나간다`() {
        failure = TmtException(ErrorCode.RECOMMENDATION_FAILED)

        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .requestAttr(USER_ID, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_FAILED"))
    }

    @Test
    fun `추천도 인증이 필요하다`() {
        mockMvc
            .perform(
                post("/v1/recommendations/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"placeIds":["place_1","place_9"]}"""),
            ).andExpect(status().isUnauthorized)
    }

    private fun item(
        placeId: Long,
        thumbnailUrl: String? = "https://cdn.example.com/a.jpg",
    ) = ReviewedPlaceView(
        placeId = placeId,
        name = "온화커피",
        categoryId = "cat_cafe",
        thumbnailUrl = thumbnailUrl,
        latestReviewedAt = REVIEWED_AT,
    )

    private fun view() =
        PlaceRecommendationView(
            placeId = 31L,
            name = "델리스피자",
            roadAddress = "서울 마포구 도화동 200-14",
            categoryName = "양식",
            thumbnailUrl = "https://cdn.example.com/b.jpg",
            summary =
                PlaceRecommendationView.Summary(
                    reviewId = 77L,
                    pros = "분위기가 좋아요",
                    cons = null,
                ),
        )

    private companion object {
        const val USER_ID = UserIdArgumentResolver.USER_ID_ATTRIBUTE
        val REVIEWED_AT: Instant = Instant.parse("2026-09-06T09:11:03.412Z")
    }
}
