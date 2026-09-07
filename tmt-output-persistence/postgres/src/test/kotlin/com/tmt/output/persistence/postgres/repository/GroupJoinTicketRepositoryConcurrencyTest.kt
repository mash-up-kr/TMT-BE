package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupJoinTicketStatus
import com.tmt.output.persistence.postgres.support.InterleavedTransactions
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 티켓 소비의 동시 경합 (T3·T7, TX-3).
 *
 * 한 사용자가 두 그룹에 거의 동시에 가입 요청하면 둘 다 "티켓 있음"을 읽는다. 조건부 UPDATE의
 * 갱신 건수가 심판이고, `FOR UPDATE SKIP LOCKED`라 뒤에 온 쪽은 **기다리지 않고** 다른 장을 집거나
 * 0행을 받는다 — 그래서 `followerBlocked`는 여기서 항상 false여야 한다.
 */
class GroupJoinTicketRepositoryConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: GroupJoinTicketRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val interleaved by lazy { InterleavedTransactions(transactionManager, jdbcTemplate) }
    private val transaction by lazy { TransactionTemplate(transactionManager) }

    @Test
    fun `티켓이 한 장이면 동시에 두 그룹에 가입해도 한 번만 소비된다`() {
        val userId = fixtures.newUser()
        fixtures.newTicket(userId)
        val (groupA, groupB) = fixtures.newGroup(fixtures.newUser()) to fixtures.newGroup(fixtures.newUser())

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.consumeOne(userId, groupA) },
                follower = { repository.consumeOne(userId, groupB) },
            )

        assertEquals(1, result.leaderResult)
        assertEquals(0, result.followerResult, "후행은 잠긴 장을 건너뛰고 남은 장이 없어 0행을 받아야 한다")
        assertFalse(result.followerBlocked, "SKIP LOCKED라 후행이 락을 기다리면 안 된다")
        assertEquals(0, availableCount(userId))
        assertEquals(listOf(groupA), consumedGroupIds(userId))
    }

    @Test
    fun `티켓이 두 장이면 동시에 두 그룹에 가입해도 서로 다른 장을 집는다`() {
        val userId = fixtures.newUser()
        val older = fixtures.newTicket(userId)
        val newer = fixtures.newTicket(userId)
        val (groupA, groupB) = fixtures.newGroup(fixtures.newUser()) to fixtures.newGroup(fixtures.newUser())

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.consumeOne(userId, groupA) },
                follower = { repository.consumeOne(userId, groupB) },
            )

        assertEquals(1, result.leaderResult)
        assertEquals(1, result.followerResult, "잠기지 않은 다음 장을 집어야 한다")
        assertFalse(result.followerBlocked)
        assertEquals(0, availableCount(userId))
        // 발급 오래된 순 — 선행이 older, 후행이 newer
        assertEquals(groupA, consumedGroupOf(older))
        assertEquals(groupB, consumedGroupOf(newer))
        assertNotEquals(consumedGroupOf(older), consumedGroupOf(newer))
    }

    @Test
    fun `소비에 밀린 트랜잭션은 집을 수 있는 장을 0으로 센다`() {
        val userId = fixtures.newUser()
        fixtures.newTicket(userId)
        val groupId = fixtures.newGroup(fixtures.newUser())

        // 선행이 유일한 장을 잡은 채 커밋 전이다. 후행 입장에서 status는 아직 AVAILABLE로 보이지만
        // 집을 수는 없다 — 잔여 수를 countByUserIdAndStatus로 세면 "티켓 있는데 가입 실패"가 나간다
        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.consumeOne(userId, groupId) },
                follower = {
                    val consumable = repository.countConsumable(userId)
                    val available = repository.countByUserIdAndStatus(userId, GroupJoinTicketStatus.AVAILABLE)
                    consumable to available
                },
            )

        assertEquals(1, result.leaderResult)
        assertEquals(0 to 1, result.followerResult, "집을 수 있는 장은 0, 겉보기 잔고는 1이어야 한다")
        assertFalse(result.followerBlocked, "SKIP LOCKED라 세는 동안에도 기다리면 안 된다")
    }

    @Test
    fun `티켓이 없으면 0행이고 아무것도 바뀌지 않는다`() {
        val userId = fixtures.newUser()

        assertEquals(0, transaction.execute { repository.consumeOne(userId, fixtures.newGroup(fixtures.newUser())) })
        assertEquals(0, availableCount(userId))
    }

    @Test
    fun `가입이 선호 장을 잡은 채라도 남은 장이 있으면 회수가 성공한다`() {
        val userId = fixtures.newUser()
        val reviewId = System.nanoTime()
        // 선호 장(이 리뷰가 발급)을 발급 오래된 순 첫 장으로 — consumeOne이 정확히 이 장을 잡는다
        val preferred = fixtures.newTicket(userId, fixtures.grantReward(userId, "REVIEW", reviewId))
        val other = fixtures.newTicket(userId)
        val groupId = fixtures.newGroup(fixtures.newUser())

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.consumeOne(userId, groupId) },
                follower = { repository.revokeOneForReview(userId, reviewId) },
            )

        assertEquals(1, result.leaderResult)
        assertEquals(1, result.followerResult, "잠긴 선호 장을 건너뛰고 남은 장을 회수해야 한다 — 기다렸다 0행을 받으면 삭제가 오탐으로 막힌다 (R7)")
        assertFalse(result.followerBlocked, "SKIP LOCKED라 회수가 가입을 기다리면 안 된다")
        assertEquals("CONSUMED", statusOf(preferred))
        assertEquals("REVOKED", statusOf(other))
    }

    @Test
    fun `유일한 장을 가입이 잡았으면 회수는 기다리지 않고 0행을 받는다`() {
        val userId = fixtures.newUser()
        val reviewId = System.nanoTime()
        val only = fixtures.newTicket(userId, fixtures.grantReward(userId, "REVIEW", reviewId))
        val groupId = fixtures.newGroup(fixtures.newUser())

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.consumeOne(userId, groupId) },
                follower = { repository.revokeOneForReview(userId, reviewId) },
            )

        assertEquals(1, result.leaderResult)
        assertEquals(0, result.followerResult, "그 장은 소비가 확정될 장이다 — 가입이 롤백되면 재시도로 회수할 수 있다")
        assertFalse(result.followerBlocked)
        assertEquals("CONSUMED", statusOf(only))
    }

    @Test
    fun `경합이 없으면 이 리뷰가 발급한 장부터 회수한다 - 우선순위는 잠기지 않은 장 안에서 지켜진다`() {
        val userId = fixtures.newUser()
        val reviewId = System.nanoTime()
        val general = fixtures.newTicket(userId)
        val preferred = fixtures.newTicket(userId, fixtures.grantReward(userId, "REVIEW", reviewId))

        assertEquals(1, transaction.execute { repository.revokeOneForReview(userId, reviewId) })

        assertEquals("REVOKED", statusOf(preferred), "id가 뒤라도 이 리뷰가 발급한 장이 먼저다")
        assertEquals("AVAILABLE", statusOf(general))
    }

    private fun statusOf(ticketId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM group_join_ticket WHERE id = ?",
            String::class.java,
            ticketId,
        )!!

    private fun availableCount(userId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM group_join_ticket WHERE user_id = ? AND status = 'AVAILABLE'",
            Int::class.java,
            userId,
        )!!

    private fun consumedGroupIds(userId: Long): List<Long?> =
        jdbcTemplate.queryForList(
            "SELECT consumed_group_id FROM group_join_ticket WHERE user_id = ? AND status = 'CONSUMED' ORDER BY id",
            Long::class.java,
            userId,
        )

    private fun consumedGroupOf(ticketId: Long): Long? =
        jdbcTemplate.queryForObject(
            "SELECT consumed_group_id FROM group_join_ticket WHERE id = ?",
            Long::class.java,
            ticketId,
        )
}
