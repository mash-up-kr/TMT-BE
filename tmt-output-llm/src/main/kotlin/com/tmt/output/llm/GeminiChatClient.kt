package com.tmt.output.llm

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode

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
) : ChatJsonClient {
    private val restClient: RestClient = RestClient.builder().baseUrl(baseUrl).build()

    override val modelLabel: String get() = "gemini/$model"

    override val enabled: Boolean get() = apiKey.isNotBlank()

    override fun completeJson(
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val body =
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to userPrompt)))),
                "generationConfig" to mapOf("responseMimeType" to "application/json", "temperature" to 0.2),
            )
        val response =
            restClient
                .post()
                .uri("/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<JsonNode>()
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
