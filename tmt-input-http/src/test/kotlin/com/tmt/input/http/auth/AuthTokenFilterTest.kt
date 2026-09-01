package com.tmt.input.http.auth

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTokenFilterTest {
    private val codec = JwtTokenCodec("test-jwt-secret-that-is-32-bytes-long", Duration.ofHours(1), Duration.ofDays(30))
    private val filter = AuthTokenFilter(codec)

    @Test
    fun `유효한 Bearer 토큰이면 사용자 ID를 요청 속성에 싣고 통과시킨다`() {
        val request = request(authorization = "Bearer ${codec.issue(42L).accessToken}")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(42L, request.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE))
        assertNotNull(chain.request)
    }

    @Test
    fun `헤더가 없으면 검증 없이 통과시킨다 - 필수 여부는 리졸버가 판단한다`() {
        val request = request(authorization = null)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(request.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE))
        assertNotNull(chain.request)
    }

    @Test
    fun `Bearer 형식이 아니면 401 AUTH_TOKEN_INVALID다`() {
        val request = request(authorization = "Basic abc")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_TOKEN_INVALID"))
        assertNull(chain.request)
    }

    @Test
    fun `만료된 토큰은 401 AUTH_TOKEN_EXPIRED다`() {
        val expired =
            JwtTokenCodec("test-jwt-secret-that-is-32-bytes-long", Duration.ofSeconds(-10), Duration.ofDays(30))
        val request = request(authorization = "Bearer ${expired.issue(42L).accessToken}")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_TOKEN_EXPIRED"))
    }

    @Test
    fun `refresh 토큰을 access 자리에 꽂으면 401 AUTH_TOKEN_INVALID다`() {
        val request = request(authorization = "Bearer ${codec.issue(42L).refreshToken}")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_TOKEN_INVALID"))
    }

    @Test
    fun `로그인·재발급 경로는 잘못된 토큰이 있어도 검증하지 않는다`() {
        val request = request(authorization = "Bearer garbage", path = "/v1/auth/login/kakao")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }

    @Test
    fun `화이트리스트에 없는 auth 하위 경로는 검증한다`() {
        val request = request(authorization = "Bearer garbage", path = "/v1/auth/logout")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `소문자 bearer 스킴도 받는다 - RFC 7235는 대소문자를 구분하지 않는다`() {
        val request = request(authorization = "bearer ${codec.issue(42L).accessToken}")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(42L, request.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE))
        assertNotNull(chain.request)
    }

    private fun request(
        authorization: String?,
        path: String = "/v1/saves",
    ): MockHttpServletRequest =
        MockHttpServletRequest("GET", "/api$path").apply {
            contextPath = "/api"
            authorization?.let { addHeader(HttpHeaders.AUTHORIZATION, it) }
        }
}
