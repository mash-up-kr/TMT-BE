package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupMembershipEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GroupMembershipRepository : JpaRepository<GroupMembershipEntity, Long> {
    @Query(
        value = """
            SELECT g.id AS groupId, g.name AS name, ma.s3_key AS imageS3Key, g.owner_id AS ownerId
            FROM groups g
            LEFT JOIN media_asset ma ON ma.id = g.image_asset_id
            WHERE g.id = :groupId
        """,
        nativeQuery = true,
    )
    fun findJoinTarget(
        @Param("groupId") groupId: Long,
    ): JoinTargetView?

    /**
     * 이미 ACTIVE면 0행이다. 조회 후 INSERT로 가르면 동시 가입이 둘 다 통과한다 —
     * `membership_active_uq`(부분 인덱스)를 ON CONFLICT 대상으로 잡아 삽입 건수로 판정한다 (D5).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO group_membership (group_id, user_id, status, joined_at)
            VALUES (:groupId, :userId, 'ACTIVE', :joinedAt)
            ON CONFLICT (group_id, user_id) WHERE status = 'ACTIVE' DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
        @Param("joinedAt") joinedAt: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE group_membership
            SET status = 'LEFT', left_at = now()
            WHERE group_id = :groupId AND user_id = :userId AND status = 'ACTIVE'
        """,
        nativeQuery = true,
    )
    fun leave(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
    ): Int

    interface JoinTargetView {
        fun getGroupId(): Long

        fun getName(): String

        fun getImageS3Key(): String?

        fun getOwnerId(): Long
    }
}
