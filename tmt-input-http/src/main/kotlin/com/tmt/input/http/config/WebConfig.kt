package com.tmt.input.http.config

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.filter.RequestIdFilter
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(UserIdArgumentResolver())
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

        private const val PREFLIGHT_CACHE_SECONDS = 600L
    }
}
