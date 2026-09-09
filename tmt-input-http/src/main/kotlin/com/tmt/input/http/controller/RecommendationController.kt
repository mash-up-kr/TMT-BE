package com.tmt.input.http.controller

import com.tmt.application.port.input.GetReviewedPlacesUseCase
import com.tmt.application.port.input.PlaceRecommendationCommand
import com.tmt.application.port.input.RecommendPlaceUseCase
import com.tmt.application.port.input.ReviewedPlaceKey
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 매장 추천 (TMT-289, 명세 v2 J §5). 화면이 두 단계라 엔드포인트도 둘이다 —
 * 재료를 고르는 격자(§5-1)와 결과 카드(§5-2).
 *
 * 격자 경로가 `/v1/users/me/...`인 것은 화면이 마이페이지 안에 있기 때문이고,
 * 두 엔드포인트가 한 기능이라 컨트롤러는 여기 함께 둔다.
 */
@Tag(name = "매장 추천", description = "명세 v2 — J §5")
@RestController
class RecommendationController(
    private val getReviewedPlacesUseCase: GetReviewedPlacesUseCase,
    private val recommendPlaceUseCase: RecommendPlaceUseCase,
) {
    @Operation(
        summary = "추천 격자 — 내가 리뷰한 매장",
        description =
            "고른 매장을 `POST /v1/recommendations/places`의 `placeIds`에 그대로 싣는다. " +
                "**매장 단위**라 같은 매장에 리뷰가 여러 건이어도 한 칸이다 (S6). " +
                "`thumbnailUrl`이 null이면 화면이 `categoryId` 아이콘을 그린다 (C4-1·R11). " +
                "미완성 저장의 매장은 나오지 않는다 (R8).",
    )
    @ApiErrorCodes(ErrorCode.INVALID_CURSOR)
    @GetMapping("/v1/users/me/reviewed-places")
    fun reviewedPlaces(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<ReviewedPlaceItem> {
        val condition = CursorCondition.of(REVIEWED_PLACES_CONDITION, userId)
        val after = CursorCodec.decode(ReviewedPlaceCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)
        val slice = getReviewedPlacesUseCase.list(userId, after, pageLimit)
        val nextCursor =
            slice.items
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.let {
                    CursorCodec.encode(
                        ReviewedPlaceCursorSpec,
                        ReviewedPlaceKey(it.latestReviewedAt, it.placeId),
                        condition,
                    )
                }
        return CursorPage(
            items =
                slice.items.map {
                    ReviewedPlaceItem(
                        placeId = PublicIds.place(it.placeId),
                        name = it.name,
                        categoryId = it.categoryId,
                        thumbnailUrl = it.thumbnailUrl,
                    )
                },
            nextCursor = nextCursor,
            hasNext = slice.hasNext,
        )
    }

    @Operation(
        summary = "매장 추천받기",
        description =
            "고른 매장을 근거로 아직 리뷰하지 않은 매장 1곳을 고른다. " +
                "외부 LLM을 호출하므로 같은 요청도 호출마다 결과가 달라진다 — `재추천` 버튼이 이것에 기댄다. " +
                "`summary`는 그 매장 **최신 리뷰**의 요약을 그대로 쓴다 (A3). " +
                "매장에 리뷰가 없거나 요약이 아직 없으면 null이다 (A2).",
    )
    @ApiErrorCodes(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.PLACE_NOT_FOUND,
        ErrorCode.RECOMMENDATION_UNAVAILABLE,
        ErrorCode.RECOMMENDATION_FAILED,
        ErrorCode.LLM_MISCONFIGURED,
    )
    @PostMapping("/v1/recommendations/places")
    fun recommendPlace(
        @UserId userId: Long,
        @RequestBody request: RecommendationRequest,
    ): RecommendationResponse {
        val view =
            recommendPlaceUseCase.recommend(
                PlaceRecommendationCommand(
                    userId = userId,
                    seedPlaceIds = request.placeIds.map(PublicIds::parsePlaceId),
                ),
            )
        return RecommendationResponse(
            place =
                RecommendationResponse.RecommendedPlace(
                    placeId = PublicIds.place(view.placeId),
                    name = view.name,
                    roadAddress = view.roadAddress,
                    categoryName = view.categoryName,
                    thumbnailUrl = view.thumbnailUrl,
                ),
            summary =
                view.summary?.let {
                    RecommendationResponse.RecommendedSummary(
                        reviewId = PublicIds.review(it.reviewId),
                        pros = it.pros,
                        cons = it.cons,
                    )
                },
        )
    }

    data class ReviewedPlaceItem(
        val placeId: String,
        @field:Schema(description = "칸 아래 라벨")
        val name: String,
        @field:Schema(description = "아이콘 키 (E11 14종). 매핑 실패 매장은 null", nullable = true)
        val categoryId: String?,
        @field:Schema(description = "내가 그 매장에 쓴 최신 리뷰의 첫 사진. 사진 0장이면 null", nullable = true)
        val thumbnailUrl: String?,
    )

    data class RecommendationRequest(
        @field:Schema(
            description = "격자에서 고른 매장. 2~5개이고 중복은 받지 않는다. 전부 내가 리뷰를 쓴 매장이어야 한다",
            example = "[\"place_1\", \"place_9\"]",
        )
        val placeIds: List<String>,
    )

    data class RecommendationResponse(
        val place: RecommendedPlace,
        @field:Schema(description = "카드의 👍 / 👎 배지. 매장에 리뷰가 없거나 요약 전이면 null", nullable = true)
        val summary: RecommendedSummary?,
    ) {
        data class RecommendedPlace(
            val placeId: String,
            val name: String,
            val roadAddress: String,
            @field:Schema(nullable = true)
            val categoryName: String?,
            @field:Schema(description = "이 매장 최신 리뷰의 첫 사진 (P7). 사진 있는 리뷰가 없으면 null", nullable = true)
            val thumbnailUrl: String?,
        )

        data class RecommendedSummary(
            @field:Schema(description = "요약의 출처 리뷰")
            val reviewId: String,
            @field:Schema(nullable = true)
            val pros: String?,
            @field:Schema(nullable = true)
            val cons: String?,
        )
    }

    internal object ReviewedPlaceCursorSpec : CursorSpec<ReviewedPlaceKey> {
        override fun toKeys(key: ReviewedPlaceKey) = listOf(key.latestReviewedAt.toString(), key.placeId.toString())

        override fun fromKeys(keys: List<String>): ReviewedPlaceKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return ReviewedPlaceKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }

    companion object {
        private const val REVIEWED_PLACES_CONDITION = "USER_REVIEWED_PLACES"
    }
}
