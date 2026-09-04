package com.tmt.application.port.input

data class KakaoLoginCommand(
    val code: String,
    /** 인가 요청에 쓴 리다이렉트 URI — 토큰 교환 때 같은 값을 보내야 한다 */
    val redirectUri: String,
)

data class KakaoLoginResult(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    /** 이번 로그인으로 users 행이 만들어졌는지 — FE 온보딩 분기 기준 (TMT-273) */
    val isNewUser: Boolean,
)

interface LoginWithKakaoUseCase {
    fun login(command: KakaoLoginCommand): KakaoLoginResult
}
