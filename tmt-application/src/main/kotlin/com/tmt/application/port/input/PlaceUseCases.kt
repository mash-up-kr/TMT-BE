package com.tmt.application.port.input

import java.time.Instant

/** 가게 상세 · 핀 클릭 시트 (B §3·§4). 비로그인도 볼 수 있고 그때 찜 여부는 false다. */
interface GetPlaceDetailUseCase {
    fun get(
        viewerId: Long?,
        placeId: Long,
    ): PlaceDetailView
}

data class PlaceDetailView(
    val placeId: Long,
    val name: String,
    val categoryName: String?,
    /** rating_sum / review_count 소수 첫째 자리 (P9). 리뷰 0건이면 null */
    val averageRating: Double?,
    val reviewCount: Int,
    val photos: List<PlacePhoto>,
    val roadAddress: String,
    val latitude: Double,
    val longitude: Double,
    val phoneNumber: String?,
    val isFavorite: Boolean,
) {
    data class PlacePhoto(
        val url: String,
        val reviewId: Long,
    )
}

/** 가게 리뷰 목록 — 최신순 (createdAt DESC, reviewId DESC) 키셋 (B §3-2). */
interface GetPlaceReviewsUseCase {
    fun get(request: PlaceReviewsRequest): PlaceReviewsResult
}

data class PlaceReviewsRequest(
    val viewerId: Long?,
    val placeId: Long,
    /** 좌표가 없으면 각 카드의 distanceMeters는 null (규약 §6-3) */
    val viewerLatitude: Double? = null,
    val viewerLongitude: Double? = null,
    val after: PlaceReviewKey? = null,
    val limit: Int,
)

data class PlaceReviewKey(
    val createdAt: Instant,
    val reviewId: Long,
)

data class PlaceReviewsResult(
    val items: List<ReviewCardView>,
    val hasNext: Boolean,
) {
    val lastKey: PlaceReviewKey?
        get() = items.lastOrNull()?.let { PlaceReviewKey(it.createdAt, it.reviewId) }
}

/** 찜 토글 (F2) — PUT·DELETE 모두 멱등이다. place_favorite UNIQUE와 정합. */
interface PlaceFavoriteUseCase {
    fun add(
        userId: Long,
        placeId: Long,
    )

    fun remove(
        userId: Long,
        placeId: Long,
    )
}
