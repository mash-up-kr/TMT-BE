package com.tmt.application.port.input

/** 전 유저 활동량 랭킹 (TMT-436). */
fun interface GetUserRankingsUseCase {
    fun get(request: UserRankingsRequest): UserRankingsResult
}

data class UserRankingsRequest(
    val after: UserRankingKey?,
    val limit: Int,
)

/** (reviewCount, userId) 내림차순. 마지막 키는 유일해야 한다 — userId가 tie-breaker다. */
data class UserRankingKey(
    val reviewCount: Int,
    val userId: Long,
)

data class UserRankingsResult(
    val items: List<UserRankingView>,
    val hasNext: Boolean,
    /** 마지막 행의 정렬 키. 다음 페이지가 없으면 null이다. */
    val lastKey: UserRankingKey? = null,
)

data class UserRankingView(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    /** 그룹에 공유한 리뷰 수. 한 리뷰를 여러 그룹에 공유해도 1이다. */
    val sharedReviewCount: Int,
)
