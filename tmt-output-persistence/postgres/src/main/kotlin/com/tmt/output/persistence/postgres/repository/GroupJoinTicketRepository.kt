package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupJoinTicketEntity
import com.tmt.output.persistence.postgres.entity.GroupJoinTicketStatus
import com.tmt.output.persistence.postgres.entity.RewardGrantEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RewardGrantRepository : JpaRepository<RewardGrantEntity, Long>

interface GroupJoinTicketRepository : JpaRepository<GroupJoinTicketEntity, Long> {
    /** ticket_available_ix(user_id, id) WHERE status='AVAILABLE'를 그대로 탄다 (T5·T7). */
    fun countByUserIdAndStatus(
        userId: Long,
        status: GroupJoinTicketStatus,
    ): Int
}
