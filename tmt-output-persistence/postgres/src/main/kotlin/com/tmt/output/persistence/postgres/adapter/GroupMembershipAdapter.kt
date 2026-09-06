package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupJoinTarget
import com.tmt.application.port.output.persistence.GroupMembershipPort
import com.tmt.output.persistence.postgres.repository.GroupMembershipRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class GroupMembershipAdapter(
    private val membershipRepository: GroupMembershipRepository,
) : GroupMembershipPort {
    override fun findJoinTarget(groupId: Long): GroupJoinTarget? =
        membershipRepository.findJoinTarget(groupId)?.let {
            GroupJoinTarget(
                groupId = it.getGroupId(),
                name = it.getName(),
                imageS3Key = it.getImageS3Key(),
                ownerId = it.getOwnerId(),
            )
        }

    @Transactional
    override fun join(
        groupId: Long,
        userId: Long,
        joinedAt: Instant,
    ): Boolean = membershipRepository.insertIfAbsent(groupId, userId, joinedAt) > 0

    @Transactional
    override fun leave(
        groupId: Long,
        userId: Long,
    ): Boolean = membershipRepository.leave(groupId, userId) > 0
}
