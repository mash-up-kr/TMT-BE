package com.tmt.application.port.input

import java.time.Instant

/** 리뷰 공유 목록 (H §3-1, TMT-223) — 내 리뷰 전체 + 이 그룹 공유 여부. PUT이 전체 교체라 현재 상태를 전부 알아야 한다. */
fun interface GetReviewSharesUseCase {
    fun get(request: ReviewSharesRequest): ReviewSharesResult
}

/** 리뷰 공유 전체 교체 (H §3-2) — 보낸 목록이 최종 집합이다. 빠진 것은 해제된다. */
fun interface ReplaceReviewSharesUseCase {
    fun replace(
        groupId: Long,
        userId: Long,
        reviewIds: List<Long>,
    ): ReplaceSharesResult
}

data class ReviewSharesRequest(
    val userId: Long,
    val groupId: Long,
    val after: ReviewShareKey?,
    val limit: Int,
)

/** 최신순 (createdAt, reviewId) 내림차순 키셋. */
data class ReviewShareKey(
    val createdAt: Instant,
    val reviewId: Long,
)

data class ReviewSharesResult(
    val items: List<ReviewShareItemView>,
    /** 페이지와 무관한 전체 공유 건수 — 클라이언트가 전 페이지 수신을 검산하는 값. */
    val sharedCount: Int,
    val hasNext: Boolean,
) {
    val lastKey: ReviewShareKey? get() = items.lastOrNull()?.let { ReviewShareKey(it.createdAt, it.reviewId) }
}

data class ReviewShareItemView(
    val reviewId: Long,
    val placeName: String,
    /** 리뷰의 첫 사진 — 리뷰는 사진이 필수라 비지 않는다 (C4). */
    val thumbnailUrl: String,
    /** 본문 전체 — 화면이 두 줄로 자른다. */
    val contentPreview: String,
    val isShared: Boolean,
    val createdAt: Instant,
)

data class ReplaceSharesResult(
    val sharedReviewIds: List<Long>,
    val sharedCount: Int,
)
