package com.tmt.application.port.output.auth

/**
 * 카카오 OAuth 경계 (TMT-271). 애플리케이션은 카카오의 토큰 교환·응답 형태를 모른다.
 */
data class KakaoProfile(
    /** 카카오 회원번호 — users.kakao_id (U1) */
    val kakaoId: Long,
    /** 동의 거부·미설정이면 null */
    val nickname: String?,
    val profileImageUrl: String?,
)

interface KakaoAuthPort {
    /**
     * 인가 코드를 토큰으로 교환하고 프로필을 조회한다.
     * [redirectUri]는 인가 요청에 쓴 값과 같아야 한다 — 다르면 카카오가 교환을 거절한다.
     */
    fun fetchProfile(
        code: String,
        redirectUri: String,
    ): KakaoProfile
}
