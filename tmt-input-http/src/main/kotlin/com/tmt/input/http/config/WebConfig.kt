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
     * 로그인·재발급, 가입 완결, 내 프로필 조회. 새 경로를 여기 넣기 전에 정말 가입 전에 필요한지 확인한다.
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
                "https://ttomatto-web.vercel.app",
                "https://ttomatto-*-ttalkkakfe.vercel.app",
            )

        private val SIGNUP_EXEMPT_PATHS =
            arrayOf(
                "/v1/auth/**",
                "/v1/users/me",
                "/v1/users/me/profile",
                "/health/**",
                "/api-docs/**",
                "/v3/api-docs/**",
            )

        private const val PREFLIGHT_CACHE_SECONDS = 600L
    }
}
