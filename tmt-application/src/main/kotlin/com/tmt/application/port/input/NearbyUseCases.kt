package com.tmt.application.port.input

import java.time.Instant

/** 피드형 근처 리뷰 (B §1) — 반경은 서버 고정 1km (E1), 거리순 · (distance, reviewId) 키셋. */
interface GetNearbyReviewsUseCase {
    fun get(request: NearbyReviewsRequest): NearbyReviewsResult
}

data class NearbyReviewsRequest(
    val viewerId: Long?,
    val latitude: Double,
    val longitude: Double,
    /** 이전 페이지 마지막 행의 정렬 키. 커서 문자열의 해석·발급은 어댑터(컨트롤러) 몫이다. */
    val after: NearbyReviewKey? = null,
    val limit: Int,
)

data class NearbyReviewKey(
    val distanceMeters: Int,
    val reviewId: Long,
)

data class NearbyReviewsResult(
    val items: List<ReviewCardView>,
    val hasNext: Boolean,
) {
    /** 다음 커서의 재료 — 마지막 행의 정렬 키 */
    val lastKey: NearbyReviewKey?
        get() = items.lastOrNull()?.let { NearbyReviewKey(requireNotNull(it.distanceMeters), it.reviewId) }
}

/** ReviewCard(B §1-1)의 읽기 모델 — 근처 피드·가게 상세·홈이 같은 카드를 쓴다 (TMT-180). */
data class ReviewCardView(
    val reviewId: Long,
    val authorId: Long,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val rating: Int,
    val distanceMeters: Int?,
    val photos: List<Photo>,
    val aiSummary: AiSummary?,
    val content: String,
    val tags: List<Tag>,
    val placeId: Long,
    val placeName: String,
    val placeRegionName: String,
    /** 14종 라벨 — 카테고리 매핑 실패 매장은 null이고 FE가 미노출 처리한다 (TMT-240) */
    val placeCategoryName: String?,
    val placeFavorite: Boolean,
    val createdAt: Instant,
) {
    data class Photo(
        val photoId: Long,
        val url: String,
        val order: Int,
    )

    data class AiSummary(
        val pros: String?,
        val cons: String?,
    )

    data class Tag(
        val tagId: String,
        val label: String,
    )
}

/** 지도형 핀 (B §2-2) — 커서 없음, 리뷰 있는 매장만(E6), 최대 30개 + truncated (E8). */
interface GetNearbyPlacesUseCase {
    fun get(request: NearbyPlacesRequest): NearbyPlacesResult
}

data class NearbyPlacesRequest(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    /** 상한 초과 시 이 좌표에서 가까운 순으로 자른다. 없으면 임의 30개 */
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    /** 가게명·주소·카테고리 라벨 검색 (E9) — 목록과 같은 술어 */
    val query: String? = null,
    val curationTagId: String? = null,
)

data class NearbyPlacesResult(
    val pins: List<Pin>,
    val truncated: Boolean,
) {
    data class Pin(
        val placeId: Long,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        /** 아이콘 키 (E11). 14종 매핑에 실패하면 null */
        val categoryId: String?,
        val reviewCount: Int,
    )
}
