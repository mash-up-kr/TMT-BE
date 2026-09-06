package com.tmt.application.port.input

import java.time.Instant

/**
 * 공개 리뷰 상세 (I §6-3). 완성된 리뷰만 대상이고 미완성 저장은 조회되지 않는다 (R8).
 * 인증은 선택이다 — 개인 리뷰 열람에는 게이트가 없다 (G2).
 */
interface GetReviewDetailUseCase {
    fun get(
        viewerId: Long?,
        reviewId: Long,
    ): ReviewDetailView
}

data class ReviewDetailView(
    val reviewId: Long,
    val author: Author,
    val place: Place,
    val photos: List<Photo>,
    val tags: List<Tag>,
    /** 리뷰는 별점이 필수라 null이 아니다 (C4). */
    val rating: Int,
    val content: String,
    /** 생성 전·실패면 null (A2). */
    val aiSummary: AiSummary?,
    /** 비로그인이면 false. */
    val isMine: Boolean,
    val createdAt: Instant,
) {
    data class Author(
        val userId: Long,
        val nickname: String,
        val profileImageUrl: String?,
    )

    data class Place(
        val placeId: Long,
        val name: String,
        val roadAddress: String,
        val categoryName: String?,
    )

    data class Photo(
        val photoId: Long,
        val url: String,
        val order: Int,
    )

    data class Tag(
        val tagId: String,
        val label: String,
    )

    data class AiSummary(
        val pros: String?,
        val cons: String?,
    )
}

/**
 * 리뷰 삭제 (I §6-4). 사진까지 완전 삭제되고 저장으로 되돌아가지 않으며 티켓 1장을 회수한다
 * (R6·R7, TX-5). 회수할 `AVAILABLE` 티켓이 0장이면 삭제를 거부한다.
 */
interface DeleteReviewUseCase {
    fun delete(
        userId: Long,
        reviewId: Long,
    )
}
