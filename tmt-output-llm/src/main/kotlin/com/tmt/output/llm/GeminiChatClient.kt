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
 * Gemini — 2순위 폴백 (무료 티어: Flash 계열, 10 RPM · 250K TPM, 2026-08 확인).
 * ⚠️ 무료 티어는 프롬프트·응답이 구글 학습에 쓰일 수 있다 — 팀 인지 필요 (TMT-232 PR 참고).
 */
@Order(2)
@Component
class GeminiChatClient(
    @param:Value("\${tmt.ai-summary.gemini.api-key:}") private val apiKey: String,
    @param:Value("\${tmt.ai-summary.gemini.model:gemini-2.5-flash}") private val model: String,
    @param:Value("\${tmt.ai-summary.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") baseUrl: String,
    @param:Value("\${tmt.ai-summary.gemini.connect-timeout-seconds:2}") private val connectTimeoutSeconds: Long,
    @param:Value("\${tmt.ai-summary.gemini.read-timeout-seconds:15}") private val readTimeoutSeconds: Long,
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

    override val modelLabel: String get() = "gemini/$model"

    override val enabled: Boolean get() = apiKey.isNotBlank()

    override fun completeJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
    ): String {
        val body =
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to userPrompt)))),
                "generationConfig" to mapOf("responseMimeType" to "application/json", "temperature" to temperature),
            )
        val startedAt = System.nanoTime()
        val response =
            restClient
                .post()
                .uri("/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
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
            ?.path("candidates")
            ?.path(0)
            ?.path("content")
            ?.path("parts")
            ?.path(0)
            ?.path("text")
            ?.asString()
            ?.takeIf { it.isNotBlank() }
            ?: error("Gemini 응답에 text가 없다: $response")
    }
}
