package com.tmt.application.domain.aisummary

import com.tmt.application.port.input.SummarizePendingReviewsUseCase
import com.tmt.application.port.output.llm.PlaceReviewsToSummarize
import com.tmt.application.port.output.llm.ReviewSummaryLlmPort
import com.tmt.application.port.output.persistence.NewReviewSummary
import com.tmt.application.port.output.persistence.PendingReviewSummary
import com.tmt.application.port.output.persistence.ReviewAiSummaryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 요약 없는 리뷰를 매장 단위로 묶어 LLM으로 채운다 (TMT-232).
 *
 * LLM 호출은 어떤 트랜잭션에도 속하지 않는다 — 리뷰 작성 커밋을 LLM 지연에 묶지 않는다는
 * 티켓 원칙 그대로다. 실패한 매장은 건너뛰고 다음 배치가 다시 줍는다: 요약이 없어도
 * 화면은 정상이다 (A2 — 행 없음 = aiSummary null).
 */
@Service
class ReviewSummaryService(
    private val reviewAiSummaryPort: ReviewAiSummaryPort,
    private val reviewSummaryLlmPort: ReviewSummaryLlmPort,
    @param:Value("\${tmt.ai-summary.batch-size:100}") private val batchSize: Int,
) : SummarizePendingReviewsUseCase {
    override fun summarizePending(): Int {
        val pending = reviewAiSummaryPort.findPendingReviews(batchSize)
        if (pending.isEmpty()) return 0

        var filled = 0
        pending.groupBy { it.placeId }.forEach { (placeId, reviews) ->
            runCatching { summarizePlace(reviews) }
                .onSuccess { filled += it }
                .onFailure { e ->
                    // 한 매장의 실패가 배치 전체를 죽이면 안 된다 — 남은 매장은 계속 진행
                    logger.warn(e) { "리뷰 요약 실패 - placeId=$placeId, reviews=${reviews.size}" }
                }
        }
        if (filled > 0) logger.info { "리뷰 요약 채움 - filled=$filled / pending=${pending.size}" }
        return filled
    }

    private fun summarizePlace(reviews: List<PendingReviewSummary>): Int {
        val result =
            reviewSummaryLlmPort.summarize(
                PlaceReviewsToSummarize(
                    placeName = reviews.first().placeName,
                    reviews =
                        reviews.map {
                            PlaceReviewsToSummarize.ReviewText(it.reviewId, it.rating, it.content)
                        },
                ),
            )

        // 요청한 리뷰만 받는다 — LLM이 지어낸 id로 남의 리뷰 요약을 덮으면 안 된다
        val requested = reviews.map { it.reviewId }.toSet()
        val accepted =
            result.summaries
                .filter { it.reviewId in requested }
                .filter { it.pros != null || it.cons != null }
                .map { NewReviewSummary(it.reviewId, it.pros, it.cons, result.model) }
        if (accepted.isEmpty()) return 0

        reviewAiSummaryPort.saveSummaries(accepted)
        return accepted.size
    }

    @Scheduled(cron = "\${tmt.ai-summary.backfill-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    fun backfillOnSchedule() {
        summarizePending()
    }
}
