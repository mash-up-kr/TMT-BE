package com.tmt.application.port.output.llm

/**
 * 리뷰 요약 LLM (TMT-232). 한 매장의 미요약 리뷰를 묶어 한 번 호출하고
 * 리뷰별 요약을 돌려받는다 — 호출 수가 리뷰 수가 아니라 매장 수에 비례한다.
 */
interface ReviewSummaryLlmPort {
    /**
     * 실패(모든 프로바이더 소진)면 예외. 응답에 빠진 리뷰는 요약 없음으로 두고
     * 다음 배치가 다시 시도한다 — 지어내는 것보다 비는 쪽이 낫다 (A2: 없으면 null).
     */
    fun summarize(request: PlaceReviewsToSummarize): LlmSummaryResult
}

data class PlaceReviewsToSummarize(
    val placeName: String,
    val reviews: List<ReviewText>,
) {
    data class ReviewText(
        val reviewId: Long,
        val rating: Int,
        val content: String,
    )
}

data class LlmSummaryResult(
    /** 응답을 만든 모델 표기 — review_ai_summary.model 에 기록한다 */
    val model: String,
    val summaries: List<ReviewSummary>,
) {
    data class ReviewSummary(
        val reviewId: Long,
        val pros: String?,
        val cons: String?,
    )
}
