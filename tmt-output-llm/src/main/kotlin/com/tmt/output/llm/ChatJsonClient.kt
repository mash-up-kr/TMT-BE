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

    /** 응답 본문(JSON 텍스트)을 돌려준다. 실패는 예외로 — 체인이 다음 프로바이더로 넘어간다. */
    fun completeJson(
        systemPrompt: String,
        userPrompt: String,
    ): String
}
