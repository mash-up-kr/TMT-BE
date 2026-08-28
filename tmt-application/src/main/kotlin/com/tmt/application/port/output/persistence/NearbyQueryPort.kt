package com.tmt.application.port.output.persistence

/**
 * 근처 탐색 읽기 (TMT-228). 공간 술어(ST_DWithin·bbox)와 키셋은 SQL이 제일 잘하는
 * 일이라 어댑터가 native로 처리하고, 카드 조립(사진·태그·요약 붙이기)은 서비스가 한다.
 */
interface NearbyQueryPort {
    /**
     * 반경 안의 리뷰를 (거리, reviewId) 오름차순 키셋으로 읽는다.
     * [limit]보다 1개 더 요청해 hasNext를 판정하는 것은 호출자 몫이 아니라 여기서 한다.
     */
    fun findReviewRowsWithin(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        afterDistanceMeters: Int?,
        afterReviewId: Long?,
        limit: Int,
        viewerId: Long?,
    ): NearbyReviewRows

    /**
     * bbox 안 리뷰 보유 매장(E6). [limit]+1개를 중심 가까운 순(중심 없으면 id 순)으로
     * 읽어 truncated를 판정한다.
     */
    fun findPins(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        centerLatitude: Double?,
        centerLongitude: Double?,
        query: String?,
        /** 검색어가 카테고리 라벨에 걸린 경우의 id들 — 라벨은 서버 상수라 SQL이 모른다 (E9) */
        queryCategoryIds: List<String>,
        categoryId: String?,
        regionPrefix: String?,
        limit: Int,
    ): List<PinRow>
}

data class NearbyReviewRows(
    val rows: List<ReviewCardRow>,
    val hasNext: Boolean,
)

data class PinRow(
    val placeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val reviewCount: Int,
)
