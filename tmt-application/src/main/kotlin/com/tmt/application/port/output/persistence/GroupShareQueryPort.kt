package com.tmt.application.port.output.persistence

import java.time.Instant

/** 공유 선택 화면의 조회 (H §3-1, TMT-223). */
interface GroupShareQueryPort {
    /** 내 리뷰 최신순 + 이 그룹 공유 여부. */
    fun findMyReviewsWithShared(
        groupId: Long,
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
    ): ReviewShareRows

    /** 페이지와 무관한 내 전체 공유 건수. */
    fun countSharedByUser(
        groupId: Long,
        userId: Long,
    ): Int

    /** [reviewIds] 중 내 리뷰가 아닌 것 — 하나라도 있으면 REVIEW_NOT_FOUND (H §3-2). */
    fun findNotMine(
        userId: Long,
        reviewIds: List<Long>,
    ): List<Long>

    /** 현재 내 공유 집합 (교체 응답용). */
    fun findSharedReviewIds(
        groupId: Long,
        userId: Long,
    ): List<Long>
}

data class ReviewShareRows(
    val rows: List<ReviewShareRow>,
    val hasNext: Boolean,
)

data class ReviewShareRow(
    val reviewId: Long,
    val placeName: String,
    val thumbnailS3Key: String,
    val content: String,
    val isShared: Boolean,
    val createdAt: Instant,
)
