package com.tmt.input.http.mock

import java.util.concurrent.ConcurrentHashMap

/**
 * AI 요약은 리뷰 커밋 이후 별도 트랜잭션에서 생성된다 (A2). 따라서 **방금 작성한 리뷰에는
 * 요약이 없다** — 실서비스에서 가장 자주 마주치는 상태다.
 *
 * mock은 그 인과를 그대로 흉내낸다: 런타임에 생성된 리뷰는 여기에 행이 없어 `null`로 내려가고,
 * 부팅 시드 리뷰(남이 예전에 쓴 리뷰)만 요약을 갖는다. 행이 없으면 null인 형태가
 * DB의 `review_ai_summary` 테이블과 같다.
 */
class MockAiSummaryStore {
    data class Summary(
        val pros: String?,
        val cons: String?,
    )

    private val summaries = ConcurrentHashMap<String, Summary>()

    fun find(reviewId: String): Summary? = summaries[reviewId]

    fun put(
        reviewId: String,
        pros: String?,
        cons: String?,
    ) {
        summaries[reviewId] = Summary(pros, cons)
    }
}
