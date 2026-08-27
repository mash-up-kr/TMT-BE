package com.tmt.application.port.output.persistence

import java.time.Instant

interface PlaceQueryPort {
    /** 없으면 null — 서비스가 PLACE_NOT_FOUND로 바꾼다. */
    fun findPlaceDetail(
        placeId: Long,
        viewerId: Long?,
    ): PlaceDetailRow?

    fun existsPlace(placeId: Long): Boolean

    /** 매장 대표 사진 — 리뷰 최신순(P7), 리뷰 안에서는 photo_order 순. */
    fun findRecentPlacePhotos(
        placeId: Long,
        limit: Int,
    ): List<PlacePhotoRow>

    /** 가게 리뷰 목록 — (created_at, review_id) 내림차순 키셋. */
    fun findPlaceReviewRows(
        placeId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
    ): PlaceReviewRows
}

data class PlaceDetailRow(
    val placeId: Long,
    val name: String,
    val categoryId: String?,
    val ratingSum: Long,
    val reviewCount: Int,
    val roadAddress: String,
    val latitude: Double,
    val longitude: Double,
    val phoneNumber: String?,
    val favorite: Boolean,
)

data class PlacePhotoRow(
    val s3Key: String,
    val reviewId: Long,
)

data class PlaceReviewRows(
    val rows: List<ReviewCardRow>,
    val hasNext: Boolean,
)

/** 찜 (F2) — 쓰기 양쪽 다 멱등: UNIQUE 충돌은 무시, 없는 행 삭제는 no-op. */
interface PlaceFavoritePort {
    fun add(
        userId: Long,
        placeId: Long,
    )

    fun remove(
        userId: Long,
        placeId: Long,
    )
}
