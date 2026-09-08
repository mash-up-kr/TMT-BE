package com.tmt.input.http.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.server.PathContainer
import org.springframework.web.util.pattern.PathPatternParser

/**
 * 가입 완결 전에 통과해야 하는 경로와 막혀야 하는 경로를 고정한다 (TMT-370).
 *
 * 인터셉터가 무엇을 하는지는
 * [SignupCompletionInterceptorTest][com.tmt.input.http.auth.SignupCompletionInterceptorTest]가 보고,
 * 여기서는 **어디에 걸리는지**를 본다. 이 목록이 이 기능에서 가장 놓치기 쉬운 자리다 — 사진 업로드
 * 발급이 빠져 있어 가입 화면에서 사진을 고를 수 없던 것을 PR 리뷰에서 잡았다.
 *
 * 패턴은 context-path(`/api`)를 제외한 경로 기준으로 매칭된다 — 등록도 같은 기준이다.
 */
class SignupExemptPathsTest {
    private val patterns = WebConfig.SIGNUP_EXEMPT_PATHS.map(PathPatternParser()::parse)

    private fun isExempt(path: String) = patterns.any { it.matches(PathContainer.parsePath(path)) }

    @Test
    fun `가입을 끝내기 위해 필요한 경로는 통과한다`() {
        listOf(
            // 토큰을 받는 곳 — 여기가 막히면 가입을 시작할 수 없다
            "/v1/auth/login/kakao",
            "/v1/auth/token/refresh",
            // 가입 완결 그 자체와, 완결 여부를 FE가 확인하는 곳
            "/v1/users/me/profile",
            "/v1/users/me",
            // 가입 화면의 프로필 사진은 발급받은 assetId로만 저장할 수 있다
            "/v1/media/upload-intents",
        ).forEach { path -> assertThat(isExempt(path)).describedAs(path).isTrue() }
    }

    @Test
    fun `그 밖의 경로는 가입을 끝내야 쓸 수 있다`() {
        listOf(
            "/v1/reviews",
            "/v1/saves",
            "/v1/groups",
            "/v1/home",
            "/v1/recommendations/places",
            "/v1/places/search",
            // /v1/users/me는 통과지만 그 아래 탭은 아니다 — 가입 전에 볼 것이 없다
            "/v1/users/me/reviews",
            "/v1/users/me/tickets",
            "/v1/users/user_1",
        ).forEach { path -> assertThat(isExempt(path)).describedAs(path).isFalse() }
    }
}
