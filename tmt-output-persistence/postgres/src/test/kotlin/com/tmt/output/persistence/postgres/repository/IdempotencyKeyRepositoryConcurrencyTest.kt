package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.InterleavedTransactions
import com.tmt.output.persistence.postgres.support.PersistenceFixtures
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager

/**
 * 멱등키 선점의 동시 경합 (공통 API 규약 §9).
 *
 * 같은 `Idempotency-Key`를 가진 요청 둘이 거의 동시에 도착하면, 둘 다 사전 조회에서
 * "없음"을 받는다 — 서로의 트랜잭션이 아직 커밋 전이라 안 보이기 때문이다. 조회로는 못 막고,
 * `INSERT ... ON CONFLICT DO NOTHING`의 **삽입 건수**가 최종 심판이다.
 *
 * 진 쪽은 예외가 아니라 0을 받는다. 그래서 트랜잭션이 롤백되는 게 아니라 반환값으로 패배를
 * 알게 되고, 서비스가 그 신호를 받아 최초 응답을 재현하는 경로로 간다.
 */
class IdempotencyKeyRepositoryConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: IdempotencyKeyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val interleaved by lazy { InterleavedTransactions(transactionManager, jdbcTemplate) }

    @Test
    fun `같은 키로 동시에 들어오면 한 건만 선점한다`() {
        val userId = fixtures.newUser()
        val idemKey = newIdemKey()

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.insertIfAbsent(userId, ENDPOINT, idemKey, FINGERPRINT, 201, """{"id":1}""") },
                follower = { repository.insertIfAbsent(userId, ENDPOINT, idemKey, FINGERPRINT, 201, """{"id":2}""") },
            )

        // 후행이 선행의 커밋을 기다렸다는 증거 — 이게 false면 두 트랜잭션이 겹치지 않은 것이다
        assertTrue(result.followerBlocked, "후행이 락에 걸리지 않았다 — 경합이 재현되지 않았다")
        assertEquals(1, result.leaderResult, "선행이 선점해야 한다")
        assertEquals(0, result.followerResult, "후행은 삽입 건수 0으로 패배를 알아야 한다")
        assertEquals(1, rowCount(userId, idemKey))
    }

    @Test
    fun `패배한 쪽은 선점한 쪽의 응답을 그대로 읽는다`() {
        val userId = fixtures.newUser()
        val idemKey = newIdemKey()
        val winnerBody = """{"reviewId": 42}"""

        interleaved.followerEntersBeforeCommit(
            leader = { repository.insertIfAbsent(userId, ENDPOINT, idemKey, FINGERPRINT, 201, winnerBody) },
            follower = {
                repository.insertIfAbsent(
                    userId,
                    ENDPOINT,
                    idemKey,
                    FINGERPRINT,
                    201,
                    """{"reviewId": 99}""",
                )
            },
        )

        // 패배한 쪽의 본문은 남지 않는다 — DO NOTHING이라 덮어쓰지 않는다
        val stored =
            jdbcTemplate.queryForObject(
                "SELECT response_body::text FROM idempotency_key WHERE user_id = ? AND endpoint = ? AND idem_key = ?",
                String::class.java,
                userId,
                ENDPOINT,
                idemKey,
            )!!
        assertTrue(stored.contains("42"), "선점한 쪽의 응답이 남아야 하는데 $stored 가 남았다")
        assertFalse(stored.contains("99"))
    }

    @Test
    fun `키가 다르면 서로 막지 않는다`() {
        val userId = fixtures.newUser()
        val leaderKey = newIdemKey()
        val followerKey = newIdemKey()

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.insertIfAbsent(userId, ENDPOINT, leaderKey, FINGERPRINT, 201, "{}") },
                follower = { repository.insertIfAbsent(userId, ENDPOINT, followerKey, FINGERPRINT, 201, "{}") },
            )

        // 대조군 — 다른 행이라 락이 걸릴 이유가 없다. 앞 테스트의 followerBlocked가
        // 무조건 true로 나오는 게 아니라는 근거이기도 하다
        assertFalse(result.followerBlocked, "다른 키인데 후행이 막혔다")
        assertEquals(1, result.leaderResult)
        assertEquals(1, result.followerResult)
        assertEquals(1, rowCount(userId, leaderKey))
        assertEquals(1, rowCount(userId, followerKey))
    }

    @Test
    fun `같은 키라도 사용자가 다르면 막지 않는다`() {
        val idemKey = newIdemKey()
        val leaderUser = fixtures.newUser()
        val followerUser = fixtures.newUser()

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.insertIfAbsent(leaderUser, ENDPOINT, idemKey, FINGERPRINT, 201, "{}") },
                follower = { repository.insertIfAbsent(followerUser, ENDPOINT, idemKey, FINGERPRINT, 201, "{}") },
            )

        // PK가 (user_id, endpoint, idem_key)라 클라이언트끼리 키가 겹쳐도 서로를 막지 않는다
        assertFalse(result.followerBlocked)
        assertEquals(1, result.leaderResult)
        assertEquals(1, result.followerResult)
    }

    private fun newIdemKey() = "idem-${PersistenceFixtures.nextSequence()}"

    private fun rowCount(
        userId: Long,
        idemKey: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM idempotency_key WHERE user_id = ? AND endpoint = ? AND idem_key = ?",
            Int::class.java,
            userId,
            ENDPOINT,
            idemKey,
        )!!

    companion object {
        private const val ENDPOINT = "POST /v1/saves"
        private const val FINGERPRINT = "fingerprint-fixed"
    }
}
