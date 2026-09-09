package com.tmt.output.llm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 기동 시점에 이미 아는 사실은 기동 시점에 알린다 (docs/LOGGING.md §3-3). 키가 없으면 체인이
 * 조용히 건너뛰기만 해서, 지금까지는 **첫 사용자가 실패해야** 비활성 상태를 알 수 있었다.
 */
@Component
class LlmProviderCheck(
    private val clients: List<ChatJsonClient>,
) {
    init {
        report()
    }

    private fun report() {
        val active = clients.filter { it.enabled }
        val labels = active.joinToString(", ") { it.modelLabel }
        when {
            active.isEmpty() ->
                logger.error { "활성 LLM 프로바이더가 없다 - 요약·추천 기능을 쓸 수 없다. GROQ_API_KEY/GEMINI_API_KEY 확인" }

            active.size < clients.size ->
                logger.warn { "LLM 폴백 여력이 없다 - 활성 ${active.size}/${clients.size} ($labels)" }

            else -> logger.info { "LLM 프로바이더 ${active.size}개 활성 - $labels" }
        }
    }
}
