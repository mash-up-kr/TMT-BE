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
    /**
     * 이 시각 전에 발급된 refresh는 무효 — 로그아웃이 찍는다 (TMT-353). null이면 로그아웃한 적이 없다.
     * 기본값을 두지 않는다 — 어댑터가 매핑을 빠뜨리면 "아무도 폐기되지 않음"으로 조용히 실패하는 자리다
     */
    val tokensInvalidBefore: Instant?,
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

    /**
     * [at] 전에 발급된 토큰을 무효로 한다 — 로그아웃 (TMT-353, U8). 토큰을 개별로 저장하지 않으므로
     * 이 사용자의 전 기기가 함께 로그아웃된다. 사용자가 없으면 false — 폐기할 것이 없다는 뜻이다.
     */
    fun invalidateTokensIssuedBefore(
        userId: Long,
        at: Instant,
    ): Boolean
}
