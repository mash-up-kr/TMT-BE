package com.tmt.application.port.output.persistence

data class UserAccount(
    val id: Long,
    val kakaoId: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

interface UserAccountPort {
    fun findByKakaoId(kakaoId: Long): UserAccount?

    /** 같은 kakaoId가 이미 있으면(동시 로그인 경쟁, U1 UNIQUE) null — 호출자가 재조회한다 */
    fun create(
        kakaoId: Long,
        nickname: String,
        profileImageUrl: String?,
    ): UserAccount?
}
