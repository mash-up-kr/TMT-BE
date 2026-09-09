package com.tmt.application.port.output.persistence

interface ReviewAiSummaryPort {
    /**
     * 요약이 없는 리뷰 (A2: `review_ai_summary` 행 없음 = 미요약). 삭제된 리뷰와
     * 본문 없는(또는 공백만인) 리뷰는 제외한다 — 요약할 텍스트가 없다.
     */
    fun findPendingReviews(limit: Int): List<PendingReviewSummary>

    fun saveSummaries(summaries: List<NewReviewSummary>)
}

data class PendingReviewSummary(
    val reviewId: Long,
    val placeId: Long,
    val placeName: String,
    val rating: Int,
    val content: String,
)

/** [pros]·[cons]가 둘 다 null이면 "요약할 내용이 없다"는 기록이다 — 응답에서는 aiSummary null (TMT-392). */
data class NewReviewSummary(
    val reviewId: Long,
    val pros: String?,
    val cons: String?,
    val model: String,
)
