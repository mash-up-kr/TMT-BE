package com.tmt.application.port.output.persistence

import java.time.Instant

/** 그룹에 공유된 리뷰 목록 (D_02 §3-2, TMT-222). */
interface GroupReviewQueryPort {
    fun existsGroup(groupId: Long): Boolean

    fun isMember(
        groupId: Long,
        userId: Long,
    ): Boolean

    /** 공유 리뷰를 카드 본체 행으로 — 리뷰 최신순 (created_at, review_id) 내림차순 키셋. */
    fun findSharedReviewRows(
        groupId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
        limit: Int,
    ): PlaceReviewRows
}
