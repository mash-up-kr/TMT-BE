package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewAiSummaryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewAiSummaryRepository : JpaRepository<ReviewAiSummaryEntity, Long> {
    /**
     * 요약 없는 리뷰 + 요약 재료 (A2: 행 없음 = 미요약). 본문은 save에 있다(D1 — review는
     * 완성 사실의 표지). 공백만인 본문은 LLM을 부를 이유가 없어 뺀다. 요약 불가로 기록된 리뷰
     * (둘 다 null인 행)는 행이 있으므로 자연히 빠진다 (TMT-392). 같은 매장이 몰리도록 place_id로 정렬한다 — 서비스가 매장 단위로
     * 묶어 호출하므로, 배치 상한에 걸려도 매장이 반쪽으로 쪼개지는 일이 줄어든다.
     */
    @Query(
        value = """
            SELECT r.id AS reviewId, r.place_id AS placeId, p.name AS placeName,
                   s.rating AS rating, s.content AS content
            FROM review r
            JOIN save s ON s.id = r.save_id
            JOIN place p ON p.id = r.place_id
            LEFT JOIN review_ai_summary a ON a.review_id = r.id
            WHERE a.review_id IS NULL
              AND r.deleted_at IS NULL
              AND s.content IS NOT NULL
              -- trim() 기본은 스페이스만 지운다. 개행·탭뿐인 본문도 요약할 내용이 없다 (PR #115 리뷰)
              AND length(btrim(s.content, E' \t\n\r')) > 0
            ORDER BY r.place_id, r.id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findPendingReviews(
        @Param("limit") limit: Int,
    ): List<PendingReviewRow>

    interface PendingReviewRow {
        fun getReviewId(): Long

        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getRating(): Int

        fun getContent(): String
    }
}
