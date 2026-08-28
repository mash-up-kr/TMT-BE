package com.tmt.application.domain.place

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.GetPlaceDetailUseCase
import com.tmt.application.port.input.GetPlaceReviewsUseCase
import com.tmt.application.port.input.PlaceDetailView
import com.tmt.application.port.input.PlaceFavoriteUseCase
import com.tmt.application.port.input.PlaceReviewsRequest
import com.tmt.application.port.input.PlaceReviewsResult
import com.tmt.application.port.output.persistence.PlaceFavoritePort
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class PlaceDetailService(
    private val placeQueryPort: PlaceQueryPort,
    private val placeFavoritePort: PlaceFavoritePort,
    private val reviewCardComposer: ReviewCardComposer,
) : GetPlaceDetailUseCase,
    GetPlaceReviewsUseCase,
    PlaceFavoriteUseCase {
    override fun get(
        viewerId: Long?,
        placeId: Long,
    ): PlaceDetailView {
        val row = placeQueryPort.findPlaceDetail(placeId, viewerId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        return PlaceDetailView(
            placeId = row.placeId,
            name = row.name,
            categoryName = FoodCategories.labelOf(row.categoryId),
            // 평균 = rating_sum / review_count (P9), 소수 첫째 자리. 141k 대부분이 리뷰 0건이라 0 나눗셈 금지
            averageRating =
                row.reviewCount.takeIf { it > 0 }?.let {
                    (row.ratingSum * 10.0 / it).roundToInt() / 10.0
                },
            reviewCount = row.reviewCount,
            photos =
                placeQueryPort.findRecentPlacePhotos(placeId, MAX_PHOTOS).map {
                    PlaceDetailView.PlacePhoto(url = reviewCardComposer.mediaUrl(it.s3Key), reviewId = it.reviewId)
                },
            roadAddress = row.roadAddress,
            latitude = row.latitude,
            longitude = row.longitude,
            phoneNumber = row.phoneNumber,
            isFavorite = row.favorite,
        )
    }

    override fun get(request: PlaceReviewsRequest): PlaceReviewsResult {
        if (!placeQueryPort.existsPlace(request.placeId)) throw TmtException(ErrorCode.PLACE_NOT_FOUND)

        val page =
            placeQueryPort.findPlaceReviewRows(
                placeId = request.placeId,
                afterCreatedAt = request.after?.createdAt,
                afterReviewId = request.after?.reviewId,
                limit = request.limit,
                viewerId = request.viewerId,
                viewerLatitude = request.viewerLatitude,
                viewerLongitude = request.viewerLongitude,
            )
        return PlaceReviewsResult(reviewCardComposer.compose(page.rows), hasNext = page.hasNext)
    }

    override fun add(
        userId: Long,
        placeId: Long,
    ) {
        if (!placeQueryPort.existsPlace(placeId)) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        placeFavoritePort.add(userId, placeId)
    }

    override fun remove(
        userId: Long,
        placeId: Long,
    ) {
        if (!placeQueryPort.existsPlace(placeId)) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        placeFavoritePort.remove(userId, placeId)
    }

    companion object {
        // 매장 사진은 리뷰에서 파생하므로(P7) 상한이 없으면 무한히 늘어난다.
        // 최신순 5장 — 그룹 커버(G16)와 같은 수 (mock에서 정한 값, 계약 문서 §4 기록)
        const val MAX_PHOTOS = 5
    }
}
