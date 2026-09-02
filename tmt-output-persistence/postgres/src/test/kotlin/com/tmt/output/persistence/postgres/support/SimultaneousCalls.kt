package com.tmt.output.persistence.postgres.support

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 같은 작업을 여러 스레드에서 한꺼번에 던진다.
 *
 * [InterleavedTransactions]가 두 트랜잭션의 순서를 손으로 짜는 것과 달리, 여기서는 여러 개가
 * 실제로 몰리는 상황을 만든다. 잃어버린 갱신(lost update)처럼 **여럿이 겹쳐야만 드러나는**
 * 문제가 대상이다.
 *
 * 스레드를 [CountDownLatch]로 한 지점에 모았다가 동시에 출발시키지만, 그것만으로는 겹침이
 * 보장되지 않는다 — 깨우는 것과 CPU에 올라가는 것은 다른 일이고, 후자는 OS 스케줄러가 정한다.
 * 그래서 각 호출의 실행 구간을 재서 **실제로 겹친 쌍이 있었는지**를 [SimultaneousResult.overlapped]로
 * 돌려준다. 이걸 단언하지 않으면, 순차로 돌아 경합이 없었던 실행도 조용히 통과한다.
 */
class SimultaneousCalls(
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /**
     * [block]을 [times]개의 스레드에서 각자의 트랜잭션으로 동시에 실행한다.
     *
     * 스레드 수는 커넥션 풀 크기(기본 10)를 넘기지 않는다 — 넘기면 커넥션을 기다리느라
     * 오히려 직렬화돼서 경합이 사라진다.
     */
    fun <T : Any> runAll(
        times: Int,
        block: () -> T,
    ): SimultaneousResult<T> {
        require(times in 2..MAX_THREADS) { "동시 실행 수는 2 이상 $MAX_THREADS 이하여야 한다" }

        val start = CountDownLatch(1)
        val done = CountDownLatch(times)
        val results = ConcurrentLinkedQueue<T>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val spans = ConcurrentLinkedQueue<Span>()
        val executor = Executors.newFixedThreadPool(times)

        try {
            repeat(times) {
                executor.submit {
                    try {
                        check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "출발 신호를 받지 못했다" }
                        val enteredAt = System.nanoTime()
                        try {
                            results += requireNotNull(transaction.execute { block() })
                        } finally {
                            spans += Span(enteredAt, System.nanoTime())
                        }
                    } catch (e: Throwable) {
                        failures += e
                    } finally {
                        done.countDown()
                    }
                }
            }

            start.countDown()
            check(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "$TIMEOUT_SECONDS 초 안에 끝나지 않았다" }

            return SimultaneousResult(
                results = results.toList(),
                failures = failures.toList(),
                overlapped = anyOverlap(spans.toList()),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    /** 실행 구간이 겹친 쌍이 하나라도 있으면 true. 하나도 없으면 전부 순차로 돈 것이다. */
    private fun anyOverlap(spans: List<Span>): Boolean =
        spans.indices.any { i ->
            (i + 1..spans.lastIndex).any { j -> spans[i].overlaps(spans[j]) }
        }

    private data class Span(
        val enteredAt: Long,
        val leftAt: Long,
    ) {
        fun overlaps(other: Span) = enteredAt < other.leftAt && other.enteredAt < leftAt
    }

    /**
     * @param results 성공한 호출의 반환값. 순서는 보장되지 않는다
     * @param failures 예외로 끝난 호출. 데드락처럼 DB가 한쪽을 죽이는 경우가 여기 담긴다
     * @param overlapped 실행 구간이 실제로 겹쳤는지. **false면 경합이 재현되지 않은 것이라
     *   결과가 기대와 같더라도 그 테스트는 아무것도 검증하지 못했다**
     */
    data class SimultaneousResult<T : Any>(
        val results: List<T>,
        val failures: List<Throwable>,
        val overlapped: Boolean,
    )

    companion object {
        /** Hikari 기본 풀 크기(10)보다 작게 둔다 */
        private const val MAX_THREADS = 8
        private const val TIMEOUT_SECONDS = 30L
    }
}
