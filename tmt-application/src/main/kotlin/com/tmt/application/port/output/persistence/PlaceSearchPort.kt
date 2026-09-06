package com.tmt.application.port.output.persistence

/**
 * 매장 검색 읽기 (TMT-195). 근처보기 검색·칩과 리뷰 작성 1단계가 같은 쿼리를 쓴다.
 * 키셋 정렬 키는 어댑터가 SQL에서 만들고(거리 미터·유사도 점수 모두 정수),
 * 카드 조립(썸네일 붙이기·평균 계산)은 서비스가 한다.
 */
interface PlaceSearchPort {
    /** [limit]+1개를 읽어 hasNext를 판정한다. 정렬은 [PlaceSearchCriteria.sortByDistance]가 가른다. */
    fun search(criteria: PlaceSearchCriteria): PlaceSearchRows

    /** 매장별 최신 리뷰 사진 1장 (P7) — 없는 매장은 결과에 없다. */
    fun findLatestPhotoKeys(placeIds: List<Long>): Map<Long, String>
}

data class PlaceSearchCriteria(
    val query: String?,
    /** 검색어가 카테고리 라벨에 걸린 경우의 id들 — 라벨은 서버 상수라 SQL이 모른다 (E9) */
    val queryCategoryIds: List<String>,
    val categoryId: String?,
    val regionPrefix: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** null이면 반경 제한 없음 (nearbyOnly=false) */
    val radiusMeters: Int?,
    /** 좌표가 있으면 거리순, 없으면 유사도순이다 (B §2-2) */
    val sortByDistance: Boolean,
    val afterSortValue: Int?,
    val afterPlaceId: Long?,
    val limit: Int,
    val viewerId: Long?,
)

data class PlaceSearchRows(
    val rows: List<PlaceSearchRow>,
    val hasNext: Boolean,
)

data class PlaceSearchRow(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    val categoryId: String?,
    val ratingSum: Long,
    val reviewCount: Int,
    val distanceMeters: Int?,
    val favorite: Boolean,
    /** 정렬 키의 앞자리 — 거리순이면 미터, 유사도순이면 similarity×1000 정수 */
    val sortValue: Int,
)
