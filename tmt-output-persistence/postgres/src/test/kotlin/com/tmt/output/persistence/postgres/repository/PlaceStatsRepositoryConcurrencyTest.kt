package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.SimultaneousCalls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * 매장 집계 증감의 동시 경합 (P9·E6).
 *
 * 앞의 둘과 반대로 **여기서는 전부 성공해야 정상이다.** 승자를 가리는 게 아니라, 동시에 들어온
 * 증감이 하나도 빠지지 않고 반영되는지를 본다.
 *
 * 읽고 더해서 쓰는 방식이었다면 잃어버린 갱신이 난다 — 둘이 같은 값을 읽고 같은 값을 쓰면
 * 한 번의 증가가 사라진다. `count = count + 1`을 DB 안에서 하면 행 락이 걸려 직렬화된다.
 * 그 직렬화가 실제로 일어나는지가 검증 대상이다.
 */
class PlaceStatsRepositoryConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: PlaceStatsRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val simultaneous by lazy { SimultaneousCalls(transactionManager) }

    @Test
    fun `동시에 들어온 리뷰가 하나도 빠지지 않고 집계된다`() {
        val place = fixtures.newPlace()

        val result = simultaneous.runAll(CONCURRENCY) { repository.addReview(place, RATING) }

        assertTrue(result.overlapped, "동시에 실행되지 않았다 — 이 테스트는 경합을 검증하지 못했다")
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(List(CONCURRENCY) { 1 }, result.results, "증감은 전부 성공해야 한다")
        // 잃어버린 갱신이 있으면 여기서 8보다 작게 나온다
        assertEquals(CONCURRENCY, reviewCountOf(place))
        // rating_sum도 같이 본다 — 평균 별점(P9)이 이 둘의 비율이다
        assertEquals((CONCURRENCY * RATING).toLong(), ratingSumOf(place))
    }

    @Test
    fun `증가와 감소가 섞여 들어와도 최종값이 맞는다`() {
        // 8건이 쌓인 상태에서 시작한다 — 차감이 바닥 조건에 걸리지 않아야 증감이 온전히 섞인다
        val place = fixtures.newPlace(reviewCount = CONCURRENCY, ratingSum = (CONCURRENCY * RATING).toLong())
        val turn = AtomicInteger()

        val result =
            simultaneous.runAll(CONCURRENCY) {
                // 스레드마다 증가·감소를 번갈아 — 짝수 순번은 +1, 홀수 순번은 -1이라 합은 0이다
                if (turn.getAndIncrement() % 2 == 0) {
                    repository.addReview(place, RATING)
                } else {
                    repository.removeReview(place, RATING)
                }
            }

        assertTrue(result.overlapped)
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(List(CONCURRENCY) { 1 }, result.results, "증감이 전부 반영돼야 한다")
        // 증가 4건과 감소 4건이 상쇄돼 시작값으로 돌아온다
        assertEquals(CONCURRENCY, reviewCountOf(place))
        assertEquals((CONCURRENCY * RATING).toLong(), ratingSumOf(place))
    }

    @Test
    fun `동시에 차감이 몰려도 음수로 내려가지 않는다`() {
        // 1건뿐인데 4건이 동시에 차감을 시도한다 — 이어쓰기 취소가 겹치는 상황
        val place = fixtures.newPlace(reviewCount = 1, ratingSum = RATING.toLong())

        val result = simultaneous.runAll(4) { repository.removeReview(place, RATING) }

        assertTrue(result.overlapped)
        // WHERE의 바닥 조건이 나머지를 막는다 — 통과한 건 정확히 한 건
        assertEquals(1, result.results.count { it == 1 }, "차감은 한 건만 통과해야 한다")
        assertEquals(3, result.results.count { it == 0 })
        assertEquals(0, reviewCountOf(place))
        assertEquals(0L, ratingSumOf(place))
    }

    private fun reviewCountOf(placeId: Long): Int =
        jdbcTemplate.queryForObject("SELECT review_count FROM place WHERE id = ?", Int::class.java, placeId)!!

    private fun ratingSumOf(placeId: Long): Long =
        jdbcTemplate.queryForObject("SELECT rating_sum FROM place WHERE id = ?", Long::class.java, placeId)!!

    companion object {
        private const val CONCURRENCY = 8
        private const val RATING = 4
    }
}
