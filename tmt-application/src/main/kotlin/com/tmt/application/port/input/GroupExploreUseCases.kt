package com.tmt.application.port.input

/** 그룹 탐색 목록 (D_01 §2, TMT-220). */
fun interface GetGroupsUseCase {
    fun get(request: GroupsRequest): GroupsResult
}

/** 그룹명 중복 확인 (D_02 §2-2). 참고값이다 — 생성이 유일성을 다시 검증한다. */
fun interface CheckGroupNameUseCase {
    fun isAvailable(name: String): Boolean
}

enum class GroupSort {
    /** (일치 저장 수, 멤버 수, id) 내림차순 (G17) — 1차 키가 조회자마다 다르다. */
    RECOMMENDED,
    MEMBER_COUNT,
    REVIEW_COUNT,
}

data class GroupsRequest(
    val viewerId: Long?,
    /** 그룹명·한줄 소개·태그 라벨을 대상으로 한다 (G18). */
    val query: String?,
    val foodCategoryId: String?,
    val regionTagIds: List<String>,
    val sort: GroupSort,
    val after: GroupListKey?,
    val limit: Int,
)

/**
 * 커서 정렬 키 — 정렬 3종을 (k1, k2, groupId) 한 형태로 통일한다.
 * RECOMMENDED는 (일치 저장 수, 멤버 수), 나머지는 (정렬 값, 0)이다.
 */
data class GroupListKey(
    val k1: Long,
    val k2: Long,
    val groupId: Long,
)

data class GroupsResult(
    val items: List<GroupCardView>,
    val hasNext: Boolean,
) {
    val lastKey: GroupListKey? get() = items.lastOrNull()?.let { GroupListKey(it.sortKey1, it.sortKey2, it.groupId) }
}

/** GroupCard (D_01 §2) 읽기 모델 — 그룹 목록·홈 추천 그룹이 같은 카드를 쓴다. */
data class GroupCardView(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    /** 공유 리뷰의 최신 사진 1장 (G16). 공유 리뷰가 없으면 null. */
    val coverImageUrl: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    /** 내가 저장한 가게와의 일치 수 (G12) — 저장 기준이지 찜이 아니다. 비로그인이면 0. */
    val matchedSavedPlaceCount: Int,
    val sortKey1: Long,
    val sortKey2: Long,
)
