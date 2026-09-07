package com.tmt.output.persistence.postgres.repository

import com.tmt.application.domain.idempotency.IdempotencyPayloadCodec
import com.tmt.application.domain.idempotency.IdempotencyService
import com.tmt.application.domain.idempotency.IdempotentRequestTransaction
import com.tmt.application.port.input.IdempotentRequest
import com.tmt.application.port.input.IdempotentRequestUseCase
import com.tmt.application.port.input.IdempotentResult
import com.tmt.output.persistence.postgres.adapter.IdempotencyAdapter
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 같은 멱등 키의 동시 요청 — 서비스 전 구간 (규약 §9, TMT-351).
 *
 * 키 선점이 비즈니스 로직보다 먼저라, 밀린 쪽은 선점 INSERT에서 승자의 커밋을 기다렸다
 * 재현 경로를 탄다. 로직이 먼저면 밀린 쪽이 로직 안의 상호배제(SKIP LOCKED 소비)에서
 * 비즈니스 예외로 먼저 터져 재현 대신 409가 나갔다 — 타임아웃 재시도가 정확히 그 모양이다.
 */
@Import(
    IdempotencyAdapter::class,
    IdempotencyService::class,
    IdempotentRequestTransaction::class,
    IdempotencyPayloadCodec::class,
)
class IdempotencyServiceConcurrencyTest : PersistenceTest() {
    data class JoinLikeResponse(
        val consumedCount: Int,
    )

    @Autowired
    private lateinit var service: IdempotentRequestUseCase

    @Autowired
    private lateinit var ticketRepository: GroupJoinTicketRepository

    @Test
    fun `같은 멱등 키로 동시에 들어와도 진 쪽이 최초 응답을 재현한다`() {
        val userId = fixtures.newUser()
        fixtures.newTicket(userId)
        val groupId = fixtures.newGroup(fixtures.newUser())
        val idemKey = "join-${System.nanoTime()}"

        val winnerEnteredLogic = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loserLogicRuns = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            // 승자 — 가입과 같은 모양: 로직 안에서 티켓을 소비하고, 커밋을 보류한 채 멈춰 있는다
            val winner =
                executor.submit<IdempotentResult<JoinLikeResponse>> {
                    service.execute(request(userId, idemKey)) {
                        check(ticketRepository.consumeOne(userId, groupId) == 1) { "승자는 티켓을 집어야 한다" }
                        winnerEnteredLogic.countDown()
                        check(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "출발 신호를 받지 못했다" }
                        JoinLikeResponse(consumedCount = 1)
                    }
                }
            check(winnerEnteredLogic.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "승자가 로직에 들어오지 못했다" }

            // 같은 키의 재시도 — 선점 INSERT에서 승자의 커밋을 기다려야 하고, 로직은 돌면 안 된다
            val loser =
                executor.submit<IdempotentResult<JoinLikeResponse>> {
                    service.execute(request(userId, idemKey)) {
                        loserLogicRuns.incrementAndGet()
                        JoinLikeResponse(consumedCount = 0)
                    }
                }
            awaitIdempotencyInsertWaiter()
            release.countDown()

            val winnerResult = winner.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val loserResult = loser.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertFalse(winnerResult.replayed)
            assertTrue(loserResult.replayed, "진 쪽은 최초 응답을 재현해야 한다 — 409가 아니다 (규약 §9)")
            assertEquals(winnerResult.response, loserResult.response)
            assertEquals(0, loserLogicRuns.get(), "진 쪽은 비즈니스 로직을 아예 돌지 않아야 한다")
            assertEquals(0, availableCount(userId), "티켓은 한 번만 소비돼야 한다")
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun request(
        userId: Long,
        idemKey: String,
    ): IdempotentRequest<JoinLikeResponse> =
        IdempotentRequest(
            userId = userId,
            endpoint = "POST /v1/groups/group_9/memberships",
            idemKey = idemKey,
            payload = null,
            responseType = JoinLikeResponse::class.java,
            successStatus = 201,
        )

    /**
     * 진 쪽이 선점 INSERT의 유니크 대기에 들어간 것을 `pg_stat_activity`로 확인한다 —
     * 이게 없으면 승자가 진 쪽 도착 전에 커밋해, 경합이 아니라 순차 재현을 검증하게 된다.
     */
    private fun awaitIdempotencyInsertWaiter() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val waiting =
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query ILIKE '%idempotency_key%'",
                    Int::class.java,
                )!!
            if (waiting > 0) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("진 쪽이 선점 INSERT에서 대기 상태가 되지 않았다 — 경합이 재현되지 않았다")
    }

    private fun availableCount(userId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM group_join_ticket WHERE user_id = ? AND status = 'AVAILABLE'",
            Int::class.java,
            userId,
        )!!

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private const val POLL_INTERVAL_MILLIS = 20L
    }
}
