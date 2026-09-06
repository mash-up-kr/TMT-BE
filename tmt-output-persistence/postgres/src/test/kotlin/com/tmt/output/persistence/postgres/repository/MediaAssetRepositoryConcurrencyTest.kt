package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.InterleavedTransactions
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant

/**
 * 미디어 조건부 전이의 동시 경합 (TMT-177).
 *
 * 서비스는 부착 전에 `attached` 여부를 확인하지만, **검증과 UPDATE 사이에 틈이 있다** —
 * 그 사이 다른 요청이 먼저 붙일 수 있다. 그래서 최종 심판은 `WHERE status = 'STAGED'`이고,
 * 서비스는 갱신된 행 수가 요청한 개수와 다르면 `MEDIA_ALREADY_ATTACHED`를 던진다.
 *
 * 선점(`ON CONFLICT`)과 다른 점은 **행이 이미 있고 상태만 바뀐다**는 것이다. 후행은 행 락에
 * 걸려 기다렸다가, 풀린 뒤 `WHERE` 조건을 새 버전으로 다시 평가한다 — 그 순간이 검증 대상이다.
 */
class MediaAssetRepositoryConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: MediaAssetRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val interleaved by lazy { InterleavedTransactions(transactionManager, jdbcTemplate) }

    @Test
    fun `같은 사진에 동시에 부착이 들어오면 한 쪽만 전이시킨다`() {
        val owner = fixtures.newUser()
        val asset = fixtures.newMediaAsset(owner)

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.markAttached(listOf(asset), Instant.now()) },
                follower = { repository.markAttached(listOf(asset), Instant.now()) },
            )

        assertTrue(result.followerBlocked, "후행이 행 락에 걸리지 않았다 — 경합이 재현되지 않았다")
        assertEquals(1, result.leaderResult)
        // 후행은 락이 풀린 뒤 WHERE를 다시 평가한다 — 이미 ATTACHED라 대상이 없다
        assertEquals(0, result.followerResult, "선행이 커밋한 뒤에는 STAGED 조건이 성립하지 않아야 한다")
        assertEquals("ATTACHED", statusOf(asset))
    }

    @Test
    fun `사진 세 장 중 한 장을 뺏기면 부분 성공으로 끝난다`() {
        val owner = fixtures.newUser()
        val contested = fixtures.newMediaAsset(owner)
        val mine = listOf(fixtures.newMediaAsset(owner), contested, fixtures.newMediaAsset(owner))

        val result =
            interleaved.followerEntersBeforeCommit(
                // 선행은 겹치는 한 장만 가져간다
                leader = { repository.markAttached(listOf(contested), Instant.now()) },
                follower = { repository.markAttached(mine, Instant.now()) },
            )

        assertTrue(result.followerBlocked)
        assertEquals(1, result.leaderResult)
        // 서비스는 이 값이 요청한 개수(3)와 다른 것을 보고 MEDIA_ALREADY_ATTACHED를 던진다
        assertEquals(2, result.followerResult, "겹치지 않은 두 장만 전이돼야 한다")
    }

    @Test
    fun `다른 사진이면 서로 막지 않는다`() {
        val owner = fixtures.newUser()
        val leaderAsset = fixtures.newMediaAsset(owner)
        val followerAsset = fixtures.newMediaAsset(owner)

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.markAttached(listOf(leaderAsset), Instant.now()) },
                follower = { repository.markAttached(listOf(followerAsset), Instant.now()) },
            )

        // 대조군 — 다른 행이라 락이 걸릴 이유가 없다
        assertEquals(false, result.followerBlocked, "다른 사진인데 후행이 막혔다")
        assertEquals(1, result.leaderResult)
        assertEquals(1, result.followerResult)
    }

    @Test
    fun `되돌리기도 같은 조건부 전이라 한 쪽만 통과한다`() {
        val owner = fixtures.newUser()
        val asset = fixtures.newMediaAsset(owner, status = "ATTACHED")

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.markStaged(listOf(asset)) },
                follower = { repository.markStaged(listOf(asset)) },
            )

        assertTrue(result.followerBlocked)
        assertEquals(1, result.leaderResult)
        assertEquals(0, result.followerResult)
        assertEquals("STAGED", statusOf(asset))
    }

    private fun statusOf(assetId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM media_asset WHERE id = ?", String::class.java, assetId)!!
}
