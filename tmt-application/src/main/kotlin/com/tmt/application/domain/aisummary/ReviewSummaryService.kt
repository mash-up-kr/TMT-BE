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
 *
 * **호출이 성공했는데 요약할 내용이 없던 리뷰는 재시도하지 않는다** (TMT-392). 본문이 "ㅂㅈㄷ"·"123"이면
 * LLM은 규칙대로 pros·cons를 null로 주는데, 그걸 버리면 다음 배치가 같은 리뷰를 영원히 다시 보낸다 —
 * 운영에서 37건이 10분마다 재호출돼 Groq 일일 한도를 태우고 추천이 503으로 밀렸다. 그래서 둘 다 null인
 * 행을 남겨 "봤다, 없다"를 기록한다. 응답은 그 행을 aiSummary null로 매핑해 계약이 바뀌지 않는다.
 * 재시도는 **호출이 실패한 경우**(예외)에만 남는다 — 그건 시간이 지나면 나을 수 있는 쪽이다.
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
        var unsummarizable = 0
        var failed = 0
        var lastError: Throwable? = null
        val places = pending.groupBy { it.placeId }
        places.forEach { (placeId, reviews) ->
            runCatching { summarizePlace(reviews) }
                .onSuccess { (accepted, skipped) ->
                    filled += accepted
                    unsummarizable += skipped
                }
                .onFailure { e ->
                    // 한 매장의 실패가 배치 전체를 죽이면 안 된다 — 남은 매장은 계속 진행
                    failed++
                    lastError = e
                    logger.warn(e) { "리뷰 요약 실패 - placeId=$placeId, reviews=${reviews.size}" }
                }
        }
        // 한 매장만 실패한 것은 그 매장 데이터 문제일 수 있지만, 전부 실패한 것은 기능이 멈춘 것이다.
        // 응답이 없는 배치라 여기서 남기지 않으면 아무도 모른다 (docs/LOGGING.md §3-2)
        if (failed == places.size) {
            logger.error(lastError) { "리뷰 요약 배치가 전부 실패했다 - places=$failed" }
        }
        if (filled > 0) logger.info { "리뷰 요약 채움 - filled=$filled / pending=${pending.size}" }
        if (unsummarizable > 0) logger.info { "요약 불가 기록 - $unsummarizable건 (본문에 요약할 내용 없음)" }
        return filled
    }

    /** @return (저장한 요약 수, 요약 불가로 기록한 수) */
    private fun summarizePlace(reviews: List<PendingReviewSummary>): Pair<Int, Int> {
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
        // 요청했는데 요약이 안 온 리뷰 — LLM이 빠뜨렸거나 둘 다 null로 준 것. 본문은 다시 봐도 같으니
        // 여기서 끝낸다: 둘 다 null인 행이 "요약할 내용 없음"의 기록이다 (TMT-392)
        val summarized = accepted.map { it.reviewId }.toSet()
        val unsummarizable =
            (requested - summarized).map { NewReviewSummary(it, pros = null, cons = null, model = result.model) }

        reviewAiSummaryPort.saveSummaries(accepted + unsummarizable)
        return accepted.size to unsummarizable.size
    }

    @Scheduled(cron = "\${tmt.ai-summary.backfill-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    fun backfillOnSchedule() {
        summarizePending()
    }
}
