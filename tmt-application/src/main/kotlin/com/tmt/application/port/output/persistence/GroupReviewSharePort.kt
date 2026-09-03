package com.tmt.application.port.output.persistence

/**
 * 그룹에 공유된 리뷰 집합을 다룬다. 공유는 `(groupId, reviewId)`가 유일하다.
 *
 * 집계(`group_place`·`review_count`·`place_count`) 반영은 [GroupStatsPort]가 맡는다.
 */
interface GroupReviewSharePort {
    /** 가입 시 선택한 리뷰 1건을 공유한다. 이미 공유돼 있으면 아무 일도 하지 않는다. */
    fun share(
        groupId: Long,
        userId: Long,
        reviewId: Long,
    )

    /** 탈퇴 시 그 사용자가 이 그룹에 공유한 리뷰를 전부 내린다. 내린 건수를 돌려준다. */
    fun unshareAllByUser(
        groupId: Long,
        userId: Long,
    ): Int

    /** 이 리뷰가 공유돼 있는 그룹들 — 집계를 다시 맞출 대상이라 내리기 **전에** 조회한다. */
    fun findSharedGroupIds(reviewId: Long): List<Long>

    /** 리뷰가 삭제되면 공유된 모든 그룹에서 내린다. 내린 건수를 돌려준다. */
    fun unshareByReview(reviewId: Long): Int

    /**
     * 이 그룹에 대한 그 사용자의 공유를 [reviewIds]로 통째로 맞춘다 (H §3-2, TX-4).
     * 빠진 것은 해제하고 없는 것은 공유한다 — 부분 갱신이 아니다.
     */
    fun replaceUserShares(
        groupId: Long,
        userId: Long,
        reviewIds: List<Long>,
    )
}
