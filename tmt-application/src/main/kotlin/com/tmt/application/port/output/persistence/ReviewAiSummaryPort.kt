package com.tmt.application.port.output.persistence

interface ReviewAiSummaryPort {
    /**
     * 요약이 없는 리뷰 (A2: `review_ai_summary` 행 없음 = 미요약). 삭제된 리뷰와
     * 본문 없는 리뷰는 제외한다 — 요약할 텍스트가 없다.
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

data class NewReviewSummary(
    val reviewId: Long,
    val pros: String?,
    val cons: String?,
    val model: String,
)
