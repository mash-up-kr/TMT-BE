package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.output.persistence.postgres.repository.GroupReviewShareRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GroupReviewShareAdapter(
    private val groupReviewShareRepository: GroupReviewShareRepository,
) : GroupReviewSharePort {
    @Transactional
    override fun share(
        groupId: Long,
        userId: Long,
        reviewId: Long,
    ) {
        // ON CONFLICT DO NOTHING이라 0행은 "이미 공유됨"이고 멱등이 성립한 것이다 (share_uq)
        groupReviewShareRepository.share(groupId = groupId, reviewId = reviewId, userId = userId)
    }

    @Transactional
    override fun unshareAllByUser(
        groupId: Long,
        userId: Long,
    ): Int = groupReviewShareRepository.deleteByGroupIdAndUserId(groupId, userId)

    @Transactional(readOnly = true)
    override fun findSharedGroupIds(reviewId: Long): List<Long> =
        groupReviewShareRepository.findGroupIdsByReviewId(reviewId)

    @Transactional
    override fun unshareByReview(reviewId: Long): Int = groupReviewShareRepository.deleteByReviewId(reviewId)

    /** 삭제·삽입 둘 다 멱등이라(NOT IN + ON CONFLICT DO NOTHING) 재시도가 안전하다. */
    @Transactional
    override fun replaceUserShares(
        groupId: Long,
        userId: Long,
        reviewIds: List<Long>,
    ) {
        groupReviewShareRepository.deleteUserSharesNotIn(groupId, userId, reviewIds.toTypedArray())
        reviewIds.forEach { groupReviewShareRepository.share(groupId = groupId, reviewId = it, userId = userId) }
    }
}
