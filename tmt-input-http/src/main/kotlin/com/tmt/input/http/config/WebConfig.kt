package com.tmt.input.http.config

import com.tmt.input.http.auth.SignupCompletionInterceptor
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.filter.RequestIdFilter
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val signupCompletionInterceptor: SignupCompletionInterceptor,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(UserIdArgumentResolver())
        resolvers.add(IdempotencyKeyArgumentResolver())
    }

    /**
     * 가입 미완료 사용자를 막는다 (TMT-370). 제외 경로는 미완료 상태에서 반드시 부를 수 있어야 하는 것들이다 —
     * 로그인·재발급, 가입 완결, 내 프로필 조회, **그리고 프로필 사진 업로드 발급**.
     * 새 경로를 여기 넣기 전에 정말 가입 전에 필요한지 확인하고, [SignupExemptPathsTest]에 근거를 남긴다.
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(signupCompletionInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(*SIGNUP_EXEMPT_PATHS)
    }

    /**
     * 오리진에 와일드카드 서브도메인이 있어 allowedOrigins가 아닌 allowedOriginPatterns를 쓴다.
     * 매핑 패턴은 context-path(/api)를 제외한 경로 기준이다.
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOriginPatterns(*ALLOWED_ORIGIN_PATTERNS)
            .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders(RequestIdFilter.HEADER)
            .maxAge(PREFLIGHT_CACHE_SECONDS)
    }

    companion object {
        private val ALLOWED_ORIGIN_PATTERNS =
            arrayOf(
                "http://localhost:3000",
                // 실서비스 도메인 (TMT-347). vercel 주소는 프리뷰·롤백 경로로 남겨둔다 —
                // 여기 없는 오리진은 브라우저에서 전부 막히므로, 도메인이 바뀌면 이 목록이 먼저다
                "https://ttomatto.kr",
                "https://ttomatto-web.vercel.app",
                "https://ttomatto-*-ttalkkakfe.vercel.app",
            )

        /** 가입 완결 전에 부를 수 있어야 하는 경로. 근거는 [SignupExemptPathsTest]가 경로별로 고정한다 */
        internal val SIGNUP_EXEMPT_PATHS =
            arrayOf(
                "/v1/auth/**",
                "/v1/users/me",
                "/v1/users/me/profile",
                // 가입 화면의 프로필 사진은 발급받은 assetId로 저장한다 — 이 발급이 막히면
                // 사진을 고를 수 없어 가입 완결이 사진 없이만 가능해진다 (TMT-370)
                "/v1/media/upload-intents",
                "/health/**",
                "/api-docs/**",
                "/v3/api-docs/**",
            )

        private const val PREFLIGHT_CACHE_SECONDS = 600L
    }
}
