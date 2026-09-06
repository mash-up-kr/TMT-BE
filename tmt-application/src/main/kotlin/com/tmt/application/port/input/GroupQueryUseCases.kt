package com.tmt.application.port.input

import java.time.Instant

/** 그룹 상세 (D_02 §3-1, TMT-222). 없는 그룹이면 GROUP_NOT_FOUND. */
fun interface GetGroupDetailUseCase {
    fun get(
        groupId: Long,
        viewerId: Long?,
    ): GroupDetailView
}

/** 그룹 상세 리뷰 목록 (D_02 §3-2) — 미가입도 전체를 커서로 받고, 비공개 필드는 서버가 지운다 (G1). */
fun interface GetGroupReviewsUseCase {
    fun get(request: GroupReviewsRequest): GroupReviewsResult
}

data class GroupReviewsRequest(
    val viewerId: Long?,
    val groupId: Long,
    val viewerLatitude: Double?,
    val viewerLongitude: Double?,
    val after: GroupReviewKey?,
    val limit: Int,
)

/** 최신순 (createdAt, reviewId) 내림차순 키셋 (B §3-2와 같은 모양). */
data class GroupReviewKey(
    val createdAt: Instant,
    val reviewId: Long,
)

data class GroupReviewsResult(
    val items: List<ReviewCardView>,
    /** 미가입·비회원이면 true — 카드의 content·aiSummary.cons를 응답에서 지운다 (TMT-216). */
    val gated: Boolean,
    val hasNext: Boolean,
) {
    val lastKey: GroupReviewKey? get() = items.lastOrNull()?.let { GroupReviewKey(it.createdAt, it.reviewId) }
}
