package com.tmt.output.oauth.kakao

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KakaoAuthAdapterTest {
    private val httpClient = FakeKakaoHttpClient()
    private val adapter = KakaoAuthAdapter(httpClient, restApiKey = "rest-key", clientSecret = "secret")

    @Test
    fun `토큰 교환과 프로필 조회를 거쳐 KakaoProfile을 만든다`() {
        val profile = adapter.fetchProfile("auth-code", "http://localhost:3000/auth/kakao/callback")

        assertEquals(12345L, profile.kakaoId)
        assertEquals("준형이", profile.nickname)
        assertEquals("https://img", profile.profileImageUrl)

        val (url, form) = httpClient.postCalls.single()
        assertEquals(KakaoAuthAdapter.TOKEN_URL, url)
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("rest-key", form["client_id"])
        assertEquals("secret", form["client_secret"])
        assertEquals("auth-code", form["code"])
        assertEquals("http://localhost:3000/auth/kakao/callback", form["redirect_uri"])
        assertEquals(KakaoAuthAdapter.USER_ME_URL to "access-token", httpClient.getCalls.single())
    }

    @Test
    fun `키가 설정돼 있지 않으면 AUTH_KAKAO_UNAVAILABLE이고 카카오를 부르지 않는다`() {
        val bare = KakaoAuthAdapter(httpClient, restApiKey = "", clientSecret = "")

        val e = assertFailsWith<TmtException> { bare.fetchProfile("c", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_UNAVAILABLE, e.errorCode)
        assertEquals(0, httpClient.postCalls.size)
    }

    @Test
    fun `invalid_grant면 AUTH_KAKAO_CODE_INVALID다`() {
        httpClient.tokenError = httpClient.statusError(400, """{"error":"invalid_grant","error_code":"KOE320"}""")

        val e = assertFailsWith<TmtException> { adapter.fetchProfile("used-code", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_CODE_INVALID, e.errorCode)
    }

    @Test
    fun `invalid_grant가 아닌 거절은 설정 문제라 AUTH_KAKAO_UNAVAILABLE이다`() {
        httpClient.tokenError = httpClient.statusError(401, """{"error":"invalid_client","error_code":"KOE010"}""")

        val e = assertFailsWith<TmtException> { adapter.fetchProfile("c", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `네트워크 실패는 AUTH_KAKAO_UNAVAILABLE이다`() {
        httpClient.tokenError = RuntimeException("카카오 타임아웃")

        val e = assertFailsWith<TmtException> { adapter.fetchProfile("c", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `토큰 응답에 access_token이 없으면 AUTH_KAKAO_UNAVAILABLE이다`() {
        httpClient.tokenResponse = "{}"

        val e = assertFailsWith<TmtException> { adapter.fetchProfile("c", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `사용자 응답에 id가 없으면 AUTH_KAKAO_UNAVAILABLE이다`() {
        httpClient.userResponse = """{"kakao_account":{}}"""

        val e = assertFailsWith<TmtException> { adapter.fetchProfile("c", "r") }

        assertEquals(ErrorCode.AUTH_KAKAO_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `닉네임·프로필 사진 동의가 없으면 null로 넘긴다`() {
        httpClient.userResponse = """{"id":5}"""

        val profile = adapter.fetchProfile("c", "r")

        assertEquals(5L, profile.kakaoId)
        assertNull(profile.nickname)
        assertNull(profile.profileImageUrl)
    }
}
