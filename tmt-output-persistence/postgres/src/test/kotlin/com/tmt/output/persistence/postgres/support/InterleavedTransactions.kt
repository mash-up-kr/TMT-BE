package com.tmt.output.persistence.postgres.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 두 트랜잭션이 겹치는 순간을 **강제로** 만든다.
 *
 * 스레드를 동시에 띄우면 겹칠 확률만 올라갈 뿐, 실제로는 선행이 커밋을 끝낸 뒤에 후행이
 * 시작되는 경우가 대부분이다. 그러면 경합이 일어나지 않은 채로 테스트가 통과한다.
 *
 * 여기서는 순서를 손으로 짠다.
 *
 * ```
 * 선행: BEGIN → 문장 실행 → (커밋 보류)
 * 후행:          BEGIN → 같은 행에 문장 실행 → 락에 걸려 대기
 * 선행:                    COMMIT
 * 후행:                    대기 풀림 → 바뀐 상태를 보고 결과가 갈린다
 * ```
 *
 * **후행이 실제로 락에 걸렸는지를 `pg_stat_activity`로 확인한다.** 이게 없으면 두 경우가
 * 구분되지 않는다 — 정말 겹쳐서 DB가 막은 것인지, 안 겹쳐서 그냥 순차로 돈 것인지.
 * 결과값은 둘 다 같게 나오기 때문에 결과만 봐서는 알 수 없다.
 */
class InterleavedTransactions(
    transactionManager: PlatformTransactionManager,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /**
     * [follower]를 [leader]가 커밋하기 전에 진입시킨다.
     *
     * 두 람다는 각자의 트랜잭션·커넥션에서 돈다. 같은 행을 건드려야 [InterleavedResult.followerBlocked]가
     * true가 되고, 그렇지 않으면 후행이 막히지 않고 그냥 지나간다.
     */
    fun <T : Any> followerEntersBeforeCommit(
        leader: () -> T,
        follower: () -> T,
    ): InterleavedResult<T> {
        val followerPid = AtomicLong(0)
        val followerReady = CountDownLatch(1)
        val leaderHoldsRow = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val followerTask =
                executor.submit<T> {
                    transaction.execute {
                        followerPid.set(backendPid())
                        followerReady.countDown()
                        // 선행이 문장을 끝내고 커밋을 보류한 상태가 될 때까지 기다린다
                        check(leaderHoldsRow.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "선행이 출발 신호를 주지 않았다" }
                        follower()
                    }
                }

            var followerBlocked = false
            val leaderResult: T? =
                transaction.execute {
                    val result = leader()
                    leaderHoldsRow.countDown()
                    check(followerReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "후행이 트랜잭션을 열지 못했다" }
                    followerBlocked = awaitFollowerBlocked(followerPid.get()) { followerTask.isDone }
                    result
                }
            // 여기서 선행이 커밋됐다 — 후행의 대기가 풀린다

            return InterleavedResult(
                leaderResult = requireNotNull(leaderResult),
                followerResult = followerTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                followerBlocked = followerBlocked,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    /** 지금 이 트랜잭션이 쓰는 세션의 백엔드 프로세스 번호. 락 대기 여부를 이 번호로 조회한다. */
    private fun backendPid(): Long = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long::class.java)!!

    /**
     * 후행 세션이 락 대기 상태로 들어갈 때까지 폴링한다. 후행이 먼저 끝나버리면(=안 막혔으면)
     * 기다릴 이유가 없으므로 바로 false로 끝낸다.
     */
    private fun awaitFollowerBlocked(
        pid: Long,
        followerFinished: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (followerFinished()) return false
            val waiting =
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'",
                    Int::class.java,
                    pid,
                )!!
            if (waiting > 0) return true
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    /**
     * @param followerBlocked 후행이 선행의 락에 **대기했는지**. 겹침 자체는 이 값이 아니라 래치가 보장한다 —
     *   후행은 선행이 문장을 실행한 뒤에 진입하고 선행은 그 뒤에 커밋하므로, 이 값과 무관하게 두 트랜잭션은 겹친다.
     *
     *   대기하는 잠금(기본 `UPDATE`·`FOR UPDATE`)에서는 **false가 곧 "겹치지 않았다"**이므로 단언에 실패시킨다.
     *   반면 `FOR UPDATE SKIP LOCKED`는 남이 잡은 행을 기다리지 않고 건너뛰므로 **false가 정상이고,
     *   그 경우 판정은 갱신 건수로 한다** (`GroupJoinTicketRepositoryConcurrencyTest`).
     */
    data class InterleavedResult<T>(
        val leaderResult: T,
        val followerResult: T,
        val followerBlocked: Boolean,
    )

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private const val POLL_INTERVAL_MILLIS = 20L
    }
}
