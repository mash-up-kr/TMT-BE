package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.filter.RequestIdFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * `Authorization: Bearer` 액세스 토큰을 검증해 사용자 ID를 요청 속성에 싣는다 (TMT-272).
 * 헤더가 없으면 그냥 통과시킨다 — 필수 여부는 [UserIdArgumentResolver]가 `@UserId` 선언으로 판단한다.
 * 로그인·재발급만 [PUBLIC_PATHS] 정확 일치로 건너뛴다 — 접두 매칭이면 공개 경로가 소리 없이 늘어난다.
 */
@Order(AuthTokenFilter.ORDER)
@Component
class AuthTokenFilter(
    private val tokenCodec: JwtTokenCodec,
) : OncePerRequestFilter() {
    private val mapper = JsonMapper.builder().build()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.removePrefix(request.contextPath) in PUBLIC_PATHS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null) {
            filterChain.doFilter(request, response)
            return
        }

        // auth scheme은 대소문자를 구분하지 않는다 (RFC 7235) — bearer로 보내는 클라이언트도 받는다
        if (!header.regionMatches(0, BEARER_PREFIX, 0, BEARER_PREFIX.length, ignoreCase = true)) {
            write401(response, ErrorCode.AUTH_TOKEN_INVALID)
            return
        }
        val userId =
            try {
                tokenCodec.parseUserId(header.substring(BEARER_PREFIX.length).trim(), TokenUse.ACCESS)
            } catch (e: TmtException) {
                write401(response, e.errorCode)
                return
            }
        request.setAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, userId)
        filterChain.doFilter(request, response)
    }

    /** [com.tmt.input.http.exception.ExceptionAdvice]의 ProblemDetail과 같은 모양 — 필터는 어드바이스 밖이라 직접 쓴다 */
    private fun write401(
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        val body =
            buildMap {
                put("type", "about:blank")
                put("title", errorCode.defaultMessage)
                put("status", HttpServletResponse.SC_UNAUTHORIZED)
                put("code", errorCode.name)
                put("timestamp", Instant.now().toString())
                RequestIdFilter.current()?.let { put("requestId", it) }
            }
        response.writer.write(mapper.writeValueAsString(body))
    }

    companion object {
        /** [RequestIdFilter](HIGHEST_PRECEDENCE) 다음 — 401 본문에 requestId가 실려야 한다 */
        const val ORDER = Int.MIN_VALUE + 10
        private const val BEARER_PREFIX = "Bearer "

        /** 토큰 없이 접근하는 경로. 새 공개 경로는 여기에 명시적으로 추가한다 */
        private val PUBLIC_PATHS = setOf("/v1/auth/login/kakao", "/v1/auth/token/refresh")
    }
}
