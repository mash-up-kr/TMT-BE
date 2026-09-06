package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.SimultaneousCalls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager

/**
 * 그룹 파생 집계의 동시 갱신 (D3).
 *
 * `refreshShareStats`는 세 문장이다 — `group_place`를 비우고, 공유 집합에서 다시 만들고,
 * `place_count`를 행 수로 맞춘다. 증감이 아니라 통째로 다시 만드는 이유는 같은 매장에 공유가
 * 겹칠 때 증감으로는 행 수와 카운트가 어긋나기 때문이다.
 *
 * 여기서 볼 것은 승패가 아니다. 멤버 둘이 동시에 공유 집합을 바꾸든 한 명이 탈퇴하든,
 * **끝났을 때 `place_count`가 `group_place` 행 수와 같은가**만 본다. 중간 상태가 섞여
 * 남는 것이 이 집계의 실패 모드다.
 */
@Import(GroupStatsAdapter::class)
class GroupStatsAdapterConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupStatsAdapter

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val simultaneous by lazy { SimultaneousCalls(transactionManager) }

    @Test
    fun `동시에 집계를 다시 만들어도 place_count가 행 수와 같다`() {
        val group = sharedGroup(placeCount = 3, sharesPerPlace = 1)

        val result = simultaneous.runAll(CONCURRENCY) { adapter.refreshShareStats(group) }

        assertTrue(result.overlapped, "동시에 실행되지 않았다 — 이 테스트는 경합을 검증하지 못했다")
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(groupPlaceRows(group), placeCountOf(group), "place_count가 group_place 행 수와 어긋났다")
        assertEquals(3, placeCountOf(group))
    }

    @Test
    fun `같은 매장에 공유가 겹쳐도 매장 수는 행 수와 같다`() {
        // 매장 2곳에 각 3건씩 공유 — place_count는 6이 아니라 2다 (D3)
        val group = sharedGroup(placeCount = 2, sharesPerPlace = 3)

        val result = simultaneous.runAll(CONCURRENCY) { adapter.refreshShareStats(group) }

        assertTrue(result.overlapped)
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(2, placeCountOf(group), "같은 매장의 공유는 한 행으로 묶여야 한다")
        assertEquals(6, reviewCountOf(group), "리뷰 수는 공유 건수 그대로다")
        assertEquals(groupPlaceRows(group), placeCountOf(group))
    }

    @Test
    fun `삭제된 리뷰는 다시 만들 때 빠진다`() {
        val group = sharedGroup(placeCount = 2, sharesPerPlace = 1)
        // 한 매장의 유일한 공유 리뷰가 삭제되면 그 매장은 group_place에서 사라져야 한다
        jdbcTemplate.update(
            """
            UPDATE review SET deleted_at = now()
            WHERE id = (SELECT review_id FROM group_review_share WHERE group_id = ? ORDER BY review_id LIMIT 1)
            """.trimIndent(),
            group,
        )

        val result = simultaneous.runAll(CONCURRENCY) { adapter.refreshShareStats(group) }

        assertTrue(result.overlapped)
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(1, placeCountOf(group))
        assertEquals(1, reviewCountOf(group))
        assertEquals(groupPlaceRows(group), placeCountOf(group))
    }

    @Test
    fun `동시에 들어온 멤버 증감이 하나도 빠지지 않는다`() {
        val group = sharedGroup(placeCount = 1, sharesPerPlace = 1)
        val before = memberCountOf(group)

        val result = simultaneous.runAll(CONCURRENCY) { adapter.addMember(group) }

        assertTrue(result.overlapped)
        assertEquals(emptyList<Throwable>(), result.failures)
        assertEquals(before + CONCURRENCY, memberCountOf(group))
    }

    /** 매장 [placeCount]곳에 각 [sharesPerPlace]건씩 공유된 그룹을 만든다. */
    private fun sharedGroup(
        placeCount: Int,
        sharesPerPlace: Int,
    ): Long {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        repeat(placeCount) {
            val place = fixtures.newPlace()
            repeat(sharesPerPlace) {
                val review = fixtures.newPublishedReview(place, userId = owner)
                fixtures.shareReview(group, review.reviewId, owner)
            }
        }
        return group
    }

    private fun placeCountOf(groupId: Long): Int =
        jdbcTemplate.queryForObject("SELECT place_count FROM groups WHERE id = ?", Int::class.java, groupId)!!

    private fun reviewCountOf(groupId: Long): Int =
        jdbcTemplate.queryForObject("SELECT review_count FROM groups WHERE id = ?", Int::class.java, groupId)!!

    private fun memberCountOf(groupId: Long): Int =
        jdbcTemplate.queryForObject("SELECT member_count FROM groups WHERE id = ?", Int::class.java, groupId)!!

    private fun groupPlaceRows(groupId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM group_place WHERE group_id = ?",
            Int::class.java,
            groupId,
        )!!

    companion object {
        private const val CONCURRENCY = 4
    }
}
