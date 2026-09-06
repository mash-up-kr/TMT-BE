package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.output.persistence.postgres.entity.GroupJoinTicketEntity
import com.tmt.output.persistence.postgres.entity.GroupJoinTicketStatus
import com.tmt.output.persistence.postgres.entity.RewardGrantEntity
import com.tmt.output.persistence.postgres.entity.RewardSourceType
import com.tmt.output.persistence.postgres.entity.RewardType
import com.tmt.output.persistence.postgres.repository.GroupJoinTicketRepository
import com.tmt.output.persistence.postgres.repository.RewardGrantRepository
import org.springframework.stereotype.Component

@Component
class GroupJoinTicketAdapter(
    private val rewardGrantRepository: RewardGrantRepository,
    private val groupJoinTicketRepository: GroupJoinTicketRepository,
) : GroupJoinTicketPort {
    override fun countAvailable(userId: Long): Int =
        groupJoinTicketRepository.countByUserIdAndStatus(userId, GroupJoinTicketStatus.AVAILABLE)

    override fun grantForSignup(userId: Long) {
        grant(userId, RewardSourceType.SIGNUP, sourceId = userId)
    }

    override fun grantForReview(
        userId: Long,
        reviewId: Long,
    ) {
        grant(userId, RewardSourceType.REVIEW, sourceId = reviewId)
    }

    /** 근거 1건 + 티켓 1장. 근거의 (source_type, source_id, reward_type)이 UNIQUE라 중복 발급은 DB가 막는다 (T8) */
    private fun grant(
        userId: Long,
        sourceType: RewardSourceType,
        sourceId: Long,
    ) {
        val grant =
            rewardGrantRepository.save(
                RewardGrantEntity(
                    userId = userId,
                    rewardType = RewardType.GROUP_JOIN_TICKET,
                    sourceType = sourceType,
                    sourceId = sourceId,
                ),
            )
        groupJoinTicketRepository.save(GroupJoinTicketEntity(userId = userId, rewardGrantId = grant.id))
    }

    override fun revokeOneForReview(
        userId: Long,
        reviewId: Long,
    ): Boolean = groupJoinTicketRepository.revokeOneForReview(userId, reviewId) > 0

    override fun consumeOne(
        userId: Long,
        groupId: Long,
    ): Boolean = groupJoinTicketRepository.consumeOne(userId, groupId) > 0

    override fun countConsumable(userId: Long): Int = groupJoinTicketRepository.countConsumable(userId)
}
