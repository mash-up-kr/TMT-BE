package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupRegionTagEntity
import com.tmt.output.persistence.postgres.entity.GroupRegionTagId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRegionTagRepository : JpaRepository<GroupRegionTagEntity, GroupRegionTagId> {
    /**
     * 편집은 집합 교체다 (D_02 §4) — 지우고 다시 넣는다.
     *
     * clearAutomatically는 호출 트랜잭션의 영속성 컨텍스트를 통째로 비워 관리 엔티티가
     * detach된다 — 여기 필요한 건 "DELETE 전에 쓰기 지연 큐를 비운다"뿐이라 flush만 켠다 (PR #87 리뷰).
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GroupRegionTagEntity t WHERE t.id.groupId = :groupId")
    fun deleteAllByGroupId(
        @Param("groupId") groupId: Long,
    ): Int
}
