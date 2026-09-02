package com.tmt.output.llm

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode

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
) : ChatJsonClient {
    private val restClient: RestClient = RestClient.builder().baseUrl(baseUrl).build()

    override val modelLabel: String get() = "groq/$model"

    override val enabled: Boolean get() = apiKey.isNotBlank()

    override fun completeJson(
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val body =
            mapOf(
                "model" to model,
                "response_format" to mapOf("type" to "json_object"),
                "temperature" to 0.2,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to systemPrompt),
                        mapOf("role" to "user", "content" to userPrompt),
                    ),
            )
        val response =
            restClient
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<JsonNode>()
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
