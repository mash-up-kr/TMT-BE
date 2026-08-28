package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.output.persistence.postgres.repository.GroupStatsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class GroupStatsAdapter(
    private val groupStatsRepository: GroupStatsRepository,
) : GroupStatsPort {
    @Transactional
    override fun addMember(groupId: Long) {
        groupStatsRepository.addMember(groupId)
    }

    @Transactional
    override fun removeMember(groupId: Long) {
        groupStatsRepository.removeMember(groupId)
    }

    /** 세 문장이 한 트랜잭션이어야 한다 — 중간에 끊기면 place_count가 group_place 행 수와 어긋난다. */
    @Transactional
    override fun refreshShareStats(groupId: Long) {
        groupStatsRepository.clearGroupPlaces(groupId)
        groupStatsRepository.rebuildGroupPlaces(groupId)
        groupStatsRepository.refreshShareCounts(groupId, Instant.now())
    }
}
