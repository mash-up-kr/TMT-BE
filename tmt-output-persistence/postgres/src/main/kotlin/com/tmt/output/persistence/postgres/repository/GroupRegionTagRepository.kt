package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupRegionTagEntity
import com.tmt.output.persistence.postgres.entity.GroupRegionTagId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRegionTagRepository : JpaRepository<GroupRegionTagEntity, GroupRegionTagId> {
    /** 편집은 집합 교체다 (D_02 §4) — 지우고 다시 넣는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupRegionTagEntity t WHERE t.id.groupId = :groupId")
    fun deleteAllByGroupId(
        @Param("groupId") groupId: Long,
    ): Int
}
