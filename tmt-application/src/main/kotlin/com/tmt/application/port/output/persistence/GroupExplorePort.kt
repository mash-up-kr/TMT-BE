package com.tmt.application.port.output.persistence

import com.tmt.application.port.input.GroupListKey

interface GroupExplorePort {
    fun findGroupCards(query: GroupCardsQuery): GroupCardsSlice

    /** groups.name UNIQUE 제약과 같은 기준 (G6). */
    fun existsByName(name: String): Boolean
}

data class GroupCardsQuery(
    val viewerId: Long?,
    /** 그룹명·한줄 소개 ILIKE 대상. null이면 검색 없음. */
    val query: String?,
    /** 검색어가 라벨에 일치한 태그 id들 — 라벨은 DB에 없어 앱이 풀어서 넘긴다 (G18). */
    val queryFoodCategoryIds: List<String>,
    val queryRegionTagIds: List<String>,
    val foodCategoryId: String?,
    val regionTagIds: List<String>,
    /** GroupSort 이름 그대로 — 쿼리가 CASE로 정렬 키를 고른다. */
    val sort: String,
    /**
     * 이 사용자가 이미 가입한 그룹을 후보에서 뺀다. 홈 추천 캐러셀만 쓰는 규칙이고
     * 그룹 탐색 목록에는 걸지 않는다 — 탐색은 전체 목록이 맞다 (A §5-3).
     */
    val excludeJoinedBy: Long? = null,
    val after: GroupListKey?,
    val limit: Int,
)

data class GroupCardsSlice(
    val rows: List<GroupCardRow>,
    val hasNext: Boolean,
    /** 마지막 행의 정렬 키. 키셋 재료라 카드가 아니라 슬라이스가 들고 있다. */
    val lastKey: GroupListKey? = null,
)

data class GroupCardRow(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    /** 공유 리뷰 최신 사진의 s3_key. 없으면 null. */
    val coverS3Key: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val matchedSavedPlaceCount: Int,
)
