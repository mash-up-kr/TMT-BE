package com.tmt.application.domain.aisummary

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 리뷰 커밋 후 요약을 당겨 채우는 트리거 (TMT-232). 실 리뷰 작성 구현이
 * 커밋되는 트랜잭션 안에서 이 이벤트를 발행하면 된다 — 리스너는 커밋 **후**
 * 비동기로 돌아 작성 응답이 LLM 지연에 묶이지 않는다.
 *
 * 이벤트는 재시작·예외로 유실될 수 있다 — 유실분은 [ReviewSummaryService]의
 * 주기 배치가 줍는다. 이 트리거는 지연을 줄일 뿐 정합의 책임이 없다.
 */
data class ReviewCommittedEvent(
    val reviewId: Long,
    val placeId: Long,
)

@Component
class ReviewCommittedListener(
    private val reviewSummaryService: ReviewSummaryService,
) {
    @Async
    @TransactionalEventListener
    fun on(event: ReviewCommittedEvent) {
        reviewSummaryService.summarizePending()
    }
}
