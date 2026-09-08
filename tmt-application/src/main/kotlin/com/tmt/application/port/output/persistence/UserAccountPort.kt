package com.tmt.application.port.output.persistence

import java.time.Instant

data class UserAccount(
    val id: Long,
    val kakaoId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val profileImageAssetId: Long?,
    /** null이면 가입 화면을 아직 끝내지 않았다 (TMT-370) */
    val profileCompletedAt: Instant?,
)

interface UserAccountPort {
    fun findByKakaoId(kakaoId: Long): UserAccount?

    fun findById(userId: Long): UserAccount?

    /** 같은 kakaoId가 이미 있으면(동시 로그인 경쟁, U1 UNIQUE) null — 호출자가 재조회한다 */
    fun create(
        kakaoId: Long,
        nickname: String,
    ): UserAccount?

    /** 가입 완결·프로필 수정. [profileImageAssetId]가 null이면 사진 없는 상태가 된다. */
    fun updateProfile(
        userId: Long,
        nickname: String,
        profileImageAssetId: Long?,
        completedAt: Instant,
    ): UserAccount?
}
