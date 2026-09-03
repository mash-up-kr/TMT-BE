package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupJoinTicketEntity
import com.tmt.output.persistence.postgres.entity.GroupJoinTicketStatus
import com.tmt.output.persistence.postgres.entity.RewardGrantEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RewardGrantRepository : JpaRepository<RewardGrantEntity, Long>

interface GroupJoinTicketRepository : JpaRepository<GroupJoinTicketEntity, Long> {
    /** ticket_available_ix(user_id, id) WHERE status='AVAILABLE'를 그대로 탄다 (T5·T7). */
    fun countByUserIdAndStatus(
        userId: Long,
        status: GroupJoinTicketStatus,
    ): Int

    /**
     * 리뷰 삭제로 AVAILABLE 티켓 1장을 회수한다 (R7). 갱신된 행 수가 0이면 회수할 게 없다는 뜻이다.
     *
     * 고르기와 갱신이 한 문장이라 동시 요청이 같은 티켓을 두 번 회수하지 못한다 —
     * `status = 'AVAILABLE'` 술어가 바깥 UPDATE에도 한 번 더 걸려 있는 이유다.
     * 티켓은 서로 구분되지 않지만 이 리뷰가 발급한 장이 남아 있으면 그것부터 고른다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE group_join_ticket
            SET status = 'REVOKED', revoked_at = now()
            WHERE status = 'AVAILABLE'
              AND id = (
                  SELECT t.id
                  FROM group_join_ticket t
                  JOIN reward_grant g ON g.id = t.reward_grant_id
                  WHERE t.user_id = :userId AND t.status = 'AVAILABLE'
                  ORDER BY
                      CASE WHEN g.source_type = 'REVIEW' AND g.source_id = :reviewId THEN 0 ELSE 1 END,
                      t.id
                  LIMIT 1
              )
        """,
        nativeQuery = true,
    )
    fun revokeOneForReview(
        @Param("userId") userId: Long,
        @Param("reviewId") reviewId: Long,
    ): Int

    /**
     * 그룹 가입으로 AVAILABLE 티켓 1장을 발급 오래된 순으로 소비한다 (T3·T7). 0행이면 티켓이 없다.
     *
     * `FOR UPDATE SKIP LOCKED` — 다른 트랜잭션이 잡은 장은 건너뛴다. 두 그룹에 거의 동시에
     * 가입해도 티켓이 두 장이면 서로 다른 장을 집고, 한 장이면 뒤에 온 쪽이 기다리지 않고 0행을 받는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE group_join_ticket
            SET status = 'CONSUMED', consumed_group_id = :groupId, consumed_at = now()
            WHERE status = 'AVAILABLE'
              AND id = (
                  SELECT id
                  FROM group_join_ticket
                  WHERE user_id = :userId AND status = 'AVAILABLE'
                  ORDER BY id
                  LIMIT 1
                  FOR UPDATE SKIP LOCKED
              )
        """,
        nativeQuery = true,
    )
    fun consumeOne(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long,
    ): Int
}
