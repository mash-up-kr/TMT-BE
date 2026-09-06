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
    /** 아이콘 키 (E11). 14종 매핑에 실패하면 null */
    val categoryId: String?,
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

/**
 * 매장 검색 (B §2-2 · F §2-1) — 근처보기 검색·칩과 리뷰 작성 1단계가 공유한다.
 *
 * 정렬 키는 `(sortValue, placeId)` 2개다. 좌표가 오면 거리 오름차순(미터 정수),
 * 없으면 매장명 유사도 내림차순(similarity×1000 정수)이고 마지막 키인 placeId가
 * tie-breaker다 — 유사도 점수만으로는 경계 중복·누락을 막지 못한다 (TMT-195).
 */
interface SearchPlacesUseCase {
    fun search(request: PlaceSearchRequest): PlaceSearchResult
}

data class PlaceSearchRequest(
    val viewerId: Long?,
    val query: String?,
    val curationTagId: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** 기본 false — 리뷰 작성 1단계는 서울 전역에서 찾는다 (P2) */
    val nearbyOnly: Boolean = false,
    val after: PlaceSearchKey? = null,
    val limit: Int,
)

data class PlaceSearchKey(
    val sortValue: Int,
    val placeId: Long,
)

data class PlaceSearchResult(
    val items: List<PlaceCardView>,
    val hasNext: Boolean,
    /** 다음 커서의 재료 — 마지막 행의 정렬 키 */
    val lastKey: PlaceSearchKey?,
)

/** PlaceCard (B §1-2) — 검색 결과·근처 가게 목록이 같은 카드를 쓴다. */
data class PlaceCardView(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    /** 아이콘 키 (E11). 14종 매핑에 실패하면 null */
    val categoryId: String?,
    val categoryName: String?,
    val averageRating: Double?,
    val reviewCount: Int,
    val thumbnailUrl: String?,
    val distanceMeters: Int?,
    val isFavorite: Boolean,
)
