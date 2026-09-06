package com.tmt.output.llm

import com.tmt.application.port.output.llm.PlaceChoiceRequest
import com.tmt.application.port.output.llm.PlaceRecommendationLlmPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

private val logger = KotlinLogging.logger {}

/**
 * 매장 추천 LLM (TMT-289). 리뷰 요약(TMT-232)과 같은 프로바이더 체인을 쓴다 —
 * @Order 순서(Groq → Gemini)로 시도하고 키가 없거나 실패한 것은 건너뛴다.
 *
 * 요약 배치와 달리 **실패를 삼키지 않는다.** 사용자가 버튼을 누른 즉시 응답해야 하는 경로라
 * 다음 주기라는 것이 없다 — 전부 소진하면 예외고 서비스가 `RECOMMENDATION_FAILED`로 바꾼다.
 */
@Component
class ChainedPlaceRecommendationLlmAdapter(
    private val clients: List<ChatJsonClient>,
) : PlaceRecommendationLlmPort {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    override fun choose(request: PlaceChoiceRequest): Long {
        val active = clients.filter { it.enabled }
        check(active.isNotEmpty()) { "활성 LLM 프로바이더가 없다 — GROQ_API_KEY/GEMINI_API_KEY 설정 확인" }

        val allowed = request.candidates.map { it.placeId }.toSet()
        val userPrompt = mapper.writeValueAsString(request)
        var lastError: Throwable? = null
        active.forEach { client ->
            runCatching {
                val json = client.completeJson(SYSTEM_PROMPT, userPrompt)
                val picked = mapper.readValue<LlmPayload>(json).placeId
                // 후보 밖 id는 지어낸 것이다 — 삼키고 첫 후보로 때우면 "추천했다"는 사실만 남고
                // 근거가 사라진다. 다음 프로바이더에 기회를 주고, 전부 실패하면 503이 정직하다
                if (picked !in allowed) {
                    error("후보에 없는 placeId=$picked (후보 ${allowed.size}개)")
                }
                return picked
            }.onFailure { e ->
                lastError = e
                logger.warn(e) { "매장 추천 LLM 실패, 다음 프로바이더로 - provider=${client.modelLabel}" }
            }
        }
        throw IllegalStateException("모든 LLM 프로바이더 실패", lastError)
    }

    private data class LlmPayload(
        val placeId: Long,
    )

    companion object {
        val SYSTEM_PROMPT =
            """
            너는 맛집 추천가다. 입력은 사용자가 고른 매장과 그가 남긴 리뷰(seeds), 그리고
            아직 가보지 않은 후보 매장 목록(candidates)이다.

            seeds에서 이 사람의 취향을 읽고, candidates 중 가장 잘 맞을 한 곳을 고른다.

            규칙:
            - 반드시 candidates에 있는 placeId 중 하나를 고른다. 목록에 없는 값을 만들지 않는다.
            - 별점이 높은 seed가 좋아한 것, 낮은 seed가 피하고 싶은 것이다. 리뷰 본문이 있으면 그것을 우선한다.
            - 카테고리가 같다고 무조건 고르지 않는다. 같은 취향의 다른 갈래도 좋은 추천이다.
            - 설명하지 않는다. 반드시 다음 JSON 형태로만 응답한다: {"placeId":<candidates의 placeId 그대로>}
            """.trimIndent()
    }
}
