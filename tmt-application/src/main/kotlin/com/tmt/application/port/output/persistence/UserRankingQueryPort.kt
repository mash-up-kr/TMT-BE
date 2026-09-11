package com.tmt.application.port.output.persistence

import com.tmt.application.port.input.UserRankingKey

/** 유저 활동량 랭킹 읽기 (TMT-436). 키셋 페이징은 SQL이 하고, 카드 조립은 서비스가 한다. */
interface UserRankingQueryPort {
    /** (reviewCount, userId) DESC 키셋. 리뷰 0건 사용자도 포함한다. */
    fun findUserRankings(query: UserRankingsQuery): UserRankingsSlice
}

data class UserRankingsQuery(
    val after: UserRankingKey?,
    val limit: Int,
)

data class UserRankingsSlice(
    val rows: List<UserRankingRow>,
    val hasNext: Boolean,
    val lastKey: UserRankingKey? = null,
)

data class UserRankingRow(
    val userId: Long,
    val nickname: String,
    /** 카카오 값 자리 — [profileImageS3Key]가 있으면 그쪽이 정본이다 (TMT-370) */
    val profileImageUrl: String?,
    val profileImageS3Key: String?,
    val reviewCount: Int,
    val memberCount: Int,
)
