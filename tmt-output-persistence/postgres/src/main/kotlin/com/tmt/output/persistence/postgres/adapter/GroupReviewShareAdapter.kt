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
        groupReviewShareRepository.share(groupId = groupId, reviewId = reviewId, userId = userId)
    }

    @Transactional
    override fun unshareAllByUser(
        groupId: Long,
        userId: Long,
    ): Int = groupReviewShareRepository.deleteByGroupIdAndUserId(groupId, userId)

    @Transactional
    override fun unshareByReview(reviewId: Long): Int = groupReviewShareRepository.deleteByReviewId(reviewId)
}
