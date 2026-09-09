package com.tmt.application.port.input

import java.time.Instant

data class KakaoLoginCommand(
    val code: String,
    /** 인가 요청에 쓴 리다이렉트 URI — 토큰 교환 때 같은 값을 보내야 한다 */
    val redirectUri: String,
)

data class KakaoLoginResult(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    /** 이번 로그인으로 users 행이 만들어졌는지 */
    val isNewUser: Boolean,
    /** 가입 화면을 끝냈는지 — false면 FE는 가입 화면으로 보낸다 (TMT-370) */
    val profileCompleted: Boolean,
)

interface LoginWithKakaoUseCase {
    fun login(command: KakaoLoginCommand): KakaoLoginResult
}

/**
 * 로그아웃 (TMT-353, U8) — 이 사용자에게 지금까지 발급된 refresh를 전부 폐기한다.
 * 멱등이다: 이미 로그아웃했거나 폐기할 토큰이 없어도 실패가 아니다.
 */
interface LogoutUseCase {
    fun logout(userId: Long)
}

/**
 * 이 refresh로 재발급해 줘도 되나 (TMT-353). 서명은 이미 검증된 뒤라, 여기서 보는 건 서명 밖의 사유다 —
 * 로그아웃 이전 발급분, 없는 사용자. 탈퇴·차단이 생기면 같은 자리에 조건이 붙는다.
 */
interface CheckRefreshAllowedUseCase {
    fun isRefreshAllowed(
        userId: Long,
        issuedAt: Instant,
    ): Boolean
}
