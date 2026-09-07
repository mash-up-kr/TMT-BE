package com.tmt.output.llm

/**
 * "프롬프트를 주면 JSON 텍스트를 돌려주는" 최소 계약. 프로바이더(Groq·Gemini)마다
 * 하나씩 구현하고, [ChainedReviewSummaryLlmAdapter]가 순서대로 시도한다.
 */
interface ChatJsonClient {
    /** review_ai_summary.model 에 기록할 표기 (예: "groq/llama-3.3-70b-versatile") */
    val modelLabel: String

    /** 키가 없으면 비활성 — 체인에서 건너뛴다. 로컬에서 키 없이도 기동은 돼야 한다. */
    val enabled: Boolean

    /**
     * 응답 본문(JSON 텍스트)을 돌려준다. 실패는 예외로 — 체인이 다음 프로바이더로 넘어간다.
     *
     * [temperature]는 호출부가 정한다. 요약(TMT-232)은 같은 리뷰가 늘 같게 요약돼야 해서 낮고,
     * 추천(TMT-289)은 **같은 재료로도 결과가 달라져야** `재추천` 버튼이 동작해서 높다.
     */
    fun completeJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = SUMMARY_TEMPERATURE,
    ): String

    companion object {
        /** 요약용 — 같은 입력에 같은 출력이 나오는 쪽이 낫다 */
        const val SUMMARY_TEMPERATURE = 0.2

        /** 추천용 — 재추천이 같은 매장만 내놓지 않게 흔든다 */
        const val CHOICE_TEMPERATURE = 0.9
    }
}
