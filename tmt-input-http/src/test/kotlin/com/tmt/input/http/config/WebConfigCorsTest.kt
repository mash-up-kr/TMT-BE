package com.tmt.input.http.config

import com.tmt.input.http.filter.RequestIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.servlet.config.annotation.CorsRegistry

class WebConfigCorsTest {
    /** CorsRegistry가 모아둔 설정을 꺼내려면 protected 접근이 필요해 상속으로 연다. */
    private class ProbeRegistry : CorsRegistry() {
        fun configurations(): Map<String, CorsConfiguration> = getCorsConfigurations()
    }

    private val config: CorsConfiguration =
        ProbeRegistry()
            .also { WebConfig().addCorsMappings(it) }
            .configurations()
            .getValue("/**")

    @Test
    fun `로컬 개발 오리진을 허용한다`() {
        assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000")
    }

    @Test
    fun `ttomatto 고정 URL과 프리뷰 배포를 허용한다`() {
        listOf(
            "https://ttomatto-web.vercel.app",
            "https://ttomatto-git-develop-ttalkkakfe.vercel.app",
            "https://ttomatto-511eil6hh-ttalkkakfe.vercel.app",
        ).forEach { origin -> assertThat(config.checkOrigin(origin)).isEqualTo(origin) }
    }

    @Test
    fun `같은 vercel 도메인이어도 다른 프로젝트는 거부한다`() {
        assertThat(config.checkOrigin("https://someone-else.vercel.app")).isNull()
    }

    @Test
    fun `허용 목록에 없는 오리진은 거부한다`() {
        assertThat(config.checkOrigin("https://evil.example.com")).isNull()
        assertThat(config.checkOrigin("http://localhost:3001")).isNull()
    }

    @Test
    fun `프론트가 붙이는 헤더를 제한하지 않는다`() {
        assertThat(config.checkHeaders(listOf("Content-Type", "X-User-Id", "Idempotency-Key")))
            .containsExactly("Content-Type", "X-User-Id", "Idempotency-Key")
    }

    @Test
    fun `mock API가 쓰는 메서드를 모두 허용한다`() {
        listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
            .forEach { method -> assertThat(config.checkHttpMethod(method)).isNotNull() }
    }

    @Test
    fun `추적용 응답 헤더를 프론트에 노출한다`() {
        assertThat(config.exposedHeaders).contains(RequestIdFilter.HEADER)
    }
}
