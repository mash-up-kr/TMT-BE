package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.MediaAssetEntity
import com.tmt.output.persistence.postgres.entity.MediaAssetStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface MediaAssetRepository : JpaRepository<MediaAssetEntity, Long> {
    /**
     * STAGED → ATTACHED 조건부 전이 (TMT-177: 읽고 쓰면 같은 파일이 두 번 붙는다).
     * WHERE의 status 조건이 동시 부착의 최종 심판이다 — 진 쪽은 카운트에 안 잡힌다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE MediaAssetEntity m
        SET m.status = com.tmt.output.persistence.postgres.entity.MediaAssetStatus.ATTACHED, m.attachedAt = :now
        WHERE m.id IN :ids AND m.status = com.tmt.output.persistence.postgres.entity.MediaAssetStatus.STAGED
        """,
    )
    fun markAttached(
        @Param("ids") ids: Collection<Long>,
        @Param("now") now: Instant,
    ): Int

    /** 이어쓰기 교체로 빠진 사진을 재부착·TTL 정리(M4) 대상으로 되돌린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE MediaAssetEntity m
        SET m.status = com.tmt.output.persistence.postgres.entity.MediaAssetStatus.STAGED, m.attachedAt = null
        WHERE m.id IN :ids AND m.status = com.tmt.output.persistence.postgres.entity.MediaAssetStatus.ATTACHED
        """,
    )
    fun markStaged(
        @Param("ids") ids: Collection<Long>,
    ): Int

    fun findAllByStatusAndCreatedAtBefore(
        status: MediaAssetStatus,
        threshold: Instant,
    ): List<MediaAssetEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MediaAssetEntity m WHERE m.id IN :ids")
    fun deleteAllByIdIn(
        @Param("ids") ids: Collection<Long>,
    ): Int
}
