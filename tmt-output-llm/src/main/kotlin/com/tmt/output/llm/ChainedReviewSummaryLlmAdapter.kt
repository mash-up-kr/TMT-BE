package com.tmt.output.llm

import com.tmt.application.port.output.llm.LlmSummaryResult
import com.tmt.application.port.output.llm.PlaceReviewsToSummarize
import com.tmt.application.port.output.llm.ReviewSummaryLlmPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

private val logger = KotlinLogging.logger {}

/**
 * 프로바이더 체인 (TMT-232) — @Order 순서(Groq → Gemini)로 시도하고, 키가 없거나
 * 실패한 프로바이더는 건너뛴다. 전부 소진하면 예외 — 호출자(배치)가 매장 단위로
 * 삼키고 다음 주기에 재시도한다.
 */
@Component
class ChainedReviewSummaryLlmAdapter(
    private val clients: List<ChatJsonClient>,
) : ReviewSummaryLlmPort {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    override fun summarize(request: PlaceReviewsToSummarize): LlmSummaryResult {
        val active = clients.filter { it.enabled }
        check(active.isNotEmpty()) { "활성 LLM 프로바이더가 없다 — GROQ_API_KEY/GEMINI_API_KEY 설정 확인" }

        val userPrompt = mapper.writeValueAsString(UserPayload(request.placeName, request.reviews))
        var lastError: Throwable? = null
        active.forEach { client ->
            runCatching {
                val json = client.completeJson(SYSTEM_PROMPT, userPrompt)
                return parse(client.modelLabel, json)
            }.onFailure { e ->
                lastError = e
                logger.warn(e) { "LLM 호출 실패, 다음 프로바이더로 - provider=${client.modelLabel}" }
            }
        }
        throw IllegalStateException("모든 LLM 프로바이더 실패", lastError)
    }

    private fun parse(
        modelLabel: String,
        json: String,
    ): LlmSummaryResult {
        val parsed = mapper.readValue<LlmPayload>(json)
        return LlmSummaryResult(
            model = modelLabel,
            summaries =
                parsed.summaries.map {
                    LlmSummaryResult.ReviewSummary(
                        reviewId = it.reviewId,
                        pros = it.pros?.takeIf(String::isNotBlank),
                        cons = it.cons?.takeIf(String::isNotBlank),
                    )
                },
        )
    }

    private data class UserPayload(
        val placeName: String,
        val reviews: List<PlaceReviewsToSummarize.ReviewText>,
    )

    private data class LlmPayload(
        val summaries: List<Item>,
    ) {
        data class Item(
            val reviewId: Long,
            val pros: String? = null,
            val cons: String? = null,
        )
    }

    companion object {
        // 규칙을 바꾸면 이미 저장된 요약과 톤이 갈린다 — 바꿀 때는 백필 재실행을 함께 고려할 것
        val SYSTEM_PROMPT =
            """
            너는 맛집 리뷰 요약가다. 입력은 한 매장(placeName)의 리뷰 목록(reviews)이다.
            리뷰마다 좋은 점(pros)과 아쉬운 점(cons)을 각각 한국어 한 문장으로 요약한다.

            규칙:
            - 리뷰 본문에 있는 내용만 쓴다. 추측하거나 지어내지 않는다.
            - 해당 내용이 없으면 그 필드는 null로 둔다 (별점이 높고 불만이 없으면 cons는 null).
            - 문장은 "~요"체로 짧게. 이모지·해시태그는 넣지 않는다.
            - 반드시 다음 JSON 형태로만 응답한다: {"summaries":[{"reviewId":<입력의 reviewId 그대로>,"pros":"...","cons":"..."}]}
            - 입력에 없는 reviewId를 만들지 않는다.
            """.trimIndent()
    }
}
