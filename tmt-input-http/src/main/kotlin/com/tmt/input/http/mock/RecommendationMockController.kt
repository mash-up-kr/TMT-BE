package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 매장 추천 (명세 v2 J §5-2). POST인 이유는 호출마다 결과가 달라져야 하기 때문이다 —
 * mock도 같은 사용자가 다시 누르면 다음 후보를 내려 `재추천` 버튼이 동작하는 것을 보여준다.
 */
@Tag(name = "매장 추천 (mock)", description = "명세 v2 — J §5-2")
@RestController
class RecommendationMockController(
    private val mockMediaUrls: MockMediaUrls,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockAiSummaryStore: MockAiSummaryStore,
) {
    private val rotation = ConcurrentHashMap<Long, AtomicInteger>()

    @Operation(summary = "매장 추천받기", description = "내 리뷰를 근거로 아직 리뷰하지 않은 매장 1곳을 고른다 (A3).")
    @ApiErrorCodes(ErrorCode.RECOMMENDATION_UNAVAILABLE, ErrorCode.RECOMMENDATION_FAILED)
    @PostMapping("/v1/recommendations/places")
    fun recommendPlace(
        @UserId userId: Long,
    ): RecommendationResponse {
        val myReviews = mockSaveStore.findAll().filter { it.ownerId == userId && it.reviewId != null }
        if (myReviews.isEmpty()) {
            throw TmtException(ErrorCode.RECOMMENDATION_UNAVAILABLE)
        }

        // 근거는 내가 리뷰한 매장의 카테고리다 — 같은 취향의 아직 안 가본 곳을 고른다
        val myPlaceIds = myReviews.map { it.placeId }.toSet()
        val myCategories = myPlaceIds.mapNotNull { mockPlaceStore.findById(it)?.categoryName }.toSet()
        val candidates =
            mockPlaceStore
                .findAll()
                .filter { it.placeId !in myPlaceIds }
                .sortedWith(
                    compareByDescending<MockPlace> { it.categoryName in myCategories }.thenBy { it.placeId },
                )
        if (candidates.isEmpty()) {
            throw TmtException(ErrorCode.RECOMMENDATION_UNAVAILABLE)
        }

        val turn = rotation.computeIfAbsent(userId) { AtomicInteger() }.getAndIncrement()
        val place = candidates[turn % candidates.size]

        // 요약은 그 매장 최신 리뷰의 것을 그대로 쓴다 (A3). 리뷰가 없거나 요약 전이면 null (A2)
        val latestReview =
            mockSaveStore
                .findAll()
                .filter { it.placeId == place.placeId && it.reviewId != null }
                .maxByOrNull { it.createdAt }
        val summary =
            latestReview?.let { review ->
                mockAiSummaryStore.find(review.reviewId!!)?.let {
                    RecommendationResponse.RecommendedSummary(
                        reviewId = review.reviewId,
                        pros = it.pros,
                        cons = it.cons,
                    )
                }
            }

        return RecommendationResponse(
            place =
                RecommendationResponse.RecommendedPlace(
                    placeId = place.placeId,
                    name = place.name,
                    roadAddress = place.roadAddress,
                    categoryName = place.categoryName,
                    thumbnailUrl = latestReview?.photoAssetIds?.firstOrNull()?.let(mockMediaUrls::urlOf),
                ),
            summary = summary,
        )
    }

    data class RecommendationResponse(
        val place: RecommendedPlace,
        /** 매장에 리뷰가 없거나 최신 리뷰의 요약이 아직 없으면 null (A2). */
        val summary: RecommendedSummary?,
    ) {
        data class RecommendedPlace(
            val placeId: String,
            val name: String,
            val roadAddress: String,
            val categoryName: String?,
            val thumbnailUrl: String?,
        )

        data class RecommendedSummary(
            val reviewId: String,
            val pros: String?,
            val cons: String?,
        )
    }
}
