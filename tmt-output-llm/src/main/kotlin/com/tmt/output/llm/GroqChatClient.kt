package com.tmt.output.llm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Groq — 1순위 (무료 티어: 30 RPM · 6K TPM · 14,400 req/일, 2026-08 확인).
 * OpenAI 호환 chat completions에 JSON 모드로 요청한다.
 */
@Order(1)
@Component
class GroqChatClient(
    @param:Value("\${tmt.ai-summary.groq.api-key:}") private val apiKey: String,
    @param:Value("\${tmt.ai-summary.groq.model}") private val model: String,
    @param:Value("\${tmt.ai-summary.groq.base-url:https://api.groq.com/openai/v1}") baseUrl: String,
    @param:Value("\${tmt.ai-summary.groq.connect-timeout-seconds:2}") private val connectTimeoutSeconds: Long,
    @param:Value("\${tmt.ai-summary.groq.read-timeout-seconds:15}") private val readTimeoutSeconds: Long,
) : ChatJsonClient {
    /**
     * 타임아웃이 없으면 기본값이 무제한이다. 요약은 배치라 감내됐지만 추천(TMT-289)은
     * 사용자가 버튼을 누르고 기다리는 동기 호출이고 프로바이더를 순차로 시도해서,
     * 한쪽이 응답을 안 주면 톰캣 스레드가 계속 물린다 (PR #106 리뷰).
     *
     * 값은 실측 전 잠정치다 — 짧게 잡으면 되던 요청을 503으로 바꾸므로 넉넉히 두고,
     * 아래 소요 시간 로그의 p95를 보고 조인다. 지금 중요한 것은 값이 아니라 무제한이 아닌 것이다.
     */
    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                },
            ).build()

    override val modelLabel: String get() = "groq/$model"

    override val enabled: Boolean get() = apiKey.isNotBlank()

    override fun completeJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
    ): String {
        val body =
            mapOf(
                "model" to model,
                "response_format" to mapOf("type" to "json_object"),
                "temperature" to temperature,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to systemPrompt),
                        mapOf("role" to "user", "content" to userPrompt),
                    ),
            )
        val startedAt = System.nanoTime()
        val response =
            restClient
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<JsonNode>()
        // 타임아웃 잠정치를 운영 p95로 조이기 위한 실측 (PR #106 리뷰)
        logger.info {
            "LLM 응답 - provider=$modelLabel, temperature=$temperature, " +
                "elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000}"
        }
        return response
            ?.path("choices")
            ?.path(0)
            ?.path("message")
            ?.path("content")
            ?.asString()
            ?.takeIf { it.isNotBlank() }
            ?: error("Groq 응답에 content가 없다: $response")
    }
}
