package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupReviewShareEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupReviewShareRepository : JpaRepository<GroupReviewShareEntity, Long> {
    /** JPQL에 upsert가 없어 네이티브다. 중복 판정은 share_uq (group_id, review_id)에 맡긴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO group_review_share (group_id, review_id, user_id)
            VALUES (:groupId, :reviewId, :userId)
            ON CONFLICT (group_id, review_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun share(
        @Param("groupId") groupId: Long,
        @Param("reviewId") reviewId: Long,
        @Param("userId") userId: Long,
    ): Int

    /** 탈퇴하면 그 그룹에 공유했던 내 리뷰가 전부 내려간다 (G10). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupReviewShareEntity s WHERE s.groupId = :groupId AND s.userId = :userId")
    fun deleteByGroupIdAndUserId(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupReviewShareEntity s WHERE s.reviewId = :reviewId")
    fun deleteByReviewId(
        @Param("reviewId") reviewId: Long,
    ): Int

    /** 집합 교체의 삭제 쪽 (H §3-2) — 목록에 없는 내 공유를 내린다. 빈 목록이면 전부 내린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            DELETE FROM group_review_share s
            WHERE s.group_id = :groupId AND s.user_id = :userId
              AND s.review_id <> ALL (CAST(:reviewIds AS bigint[]))
        """,
        nativeQuery = true,
    )
    fun deleteUserSharesNotIn(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
        @Param("reviewIds") reviewIds: Array<Long>,
    ): Int

    /** 리뷰가 내려간 그룹들 — 집계를 다시 맞출 대상이다. 삭제 전에 조회한다. */
    @Query("SELECT s.groupId FROM GroupReviewShareEntity s WHERE s.reviewId = :reviewId")
    fun findGroupIdsByReviewId(
        @Param("reviewId") reviewId: Long,
    ): List<Long>
}
