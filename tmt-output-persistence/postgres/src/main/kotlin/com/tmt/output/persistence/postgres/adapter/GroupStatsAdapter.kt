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
    // 파생 집계 증감이다. 대상 그룹은 호출부가 이미 확인했고 FK가 존재를 보장한다
    @Transactional
    override fun addMember(groupId: Long) {
        groupStatsRepository.addMember(groupId)
    }

    /** 0행이 정상이다 — 생성자만 남으면 `memberCount > 1` 조건이 차감을 막는다 (G11). */
    @Transactional
    override fun removeMember(groupId: Long) {
        groupStatsRepository.removeMember(groupId)
    }

    /**
     * 세 문장이 한 트랜잭션이어야 한다 — 중간에 끊기면 place_count가 group_place 행 수와 어긋난다.
     * 한 트랜잭션인 것만으로는 부족해 그룹 행을 먼저 잠근다 (TMT-310).
     */
    @Transactional
    override fun refreshShareStats(groupId: Long) {
        groupStatsRepository.lockGroup(groupId)
        groupStatsRepository.clearGroupPlaces(groupId)
        groupStatsRepository.rebuildGroupPlaces(groupId)
        groupStatsRepository.refreshShareCounts(groupId, Instant.now())
    }
}
