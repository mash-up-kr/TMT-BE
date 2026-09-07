package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 공유 집합 쓰기 (H §3-2, TX-4) — TMT-348 전수 커버.
 *
 * `replaceUserShares`는 **부분 갱신이 아니라 통째 교체**다. 삭제 쪽이
 * `review_id <> ALL(...)` 배열 술어라 빈 목록의 동작(전부 내림)이 SQL에서만 드러난다.
 */
@Import(GroupReviewShareAdapter::class)
class GroupReviewShareAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupReviewShareAdapter

    @Test
    fun `같은 리뷰를 두 번 공유해도 한 번만 들어간다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)

        adapter.share(group, user, review.reviewId)
        adapter.share(group, user, review.reviewId)

        assertEquals(1, sharedCount(group, user))
    }

    @Test
    fun `교체는 빠진 것을 내리고 없는 것을 올린다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val keep = fixtures.newPublishedReview(fixtures.newPlace(), user)
        val drop = fixtures.newPublishedReview(fixtures.newPlace(), user)
        val add = fixtures.newPublishedReview(fixtures.newPlace(), user)
        adapter.share(group, user, keep.reviewId)
        adapter.share(group, user, drop.reviewId)

        adapter.replaceUserShares(group, user, listOf(keep.reviewId, add.reviewId))

        assertEquals(listOf(add.reviewId, keep.reviewId).sorted(), sharedReviewIds(group, user))
    }

    @Test
    fun `빈 목록으로 교체하면 내 공유가 전부 내려간다`() {
        // 배열 술어가 빈 배열일 때 <> ALL이 참이 되는지 — 여기가 조용히 틀리면 아무것도 안 지워진다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        repeat(2) { adapter.share(group, user, fixtures.newPublishedReview(fixtures.newPlace(), user).reviewId) }
        assertEquals(2, sharedCount(group, user))

        adapter.replaceUserShares(group, user, emptyList())

        assertEquals(0, sharedCount(group, user))
    }

    @Test
    fun `교체는 남의 공유를 건드리지 않는다`() {
        val me = fixtures.newUser()
        val other = fixtures.newUser()
        val group = fixtures.newGroup(me)
        val mine = fixtures.newPublishedReview(fixtures.newPlace(), me)
        val theirs = fixtures.newPublishedReview(fixtures.newPlace(), other)
        adapter.share(group, me, mine.reviewId)
        adapter.share(group, other, theirs.reviewId)

        adapter.replaceUserShares(group, me, emptyList())

        assertEquals(0, sharedCount(group, me))
        assertEquals(1, sharedCount(group, other), "남의 공유는 그대로다")
    }

    @Test
    fun `교체는 다른 그룹의 공유를 건드리지 않는다`() {
        val user = fixtures.newUser()
        val here = fixtures.newGroup(user)
        val elsewhere = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        adapter.share(here, user, review.reviewId)
        adapter.share(elsewhere, user, review.reviewId)

        adapter.replaceUserShares(here, user, emptyList())

        assertEquals(0, sharedCount(here, user))
        assertEquals(1, sharedCount(elsewhere, user))
    }

    @Test
    fun `리뷰가 공유된 그룹들을 찾는다`() {
        val user = fixtures.newUser()
        val g1 = fixtures.newGroup(user)
        val g2 = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        adapter.share(g1, user, review.reviewId)
        adapter.share(g2, user, review.reviewId)

        assertEquals(listOf(g1, g2).sorted(), adapter.findSharedGroupIds(review.reviewId).sorted())
    }

    @Test
    fun `리뷰 삭제로 모든 그룹에서 내린다`() {
        val user = fixtures.newUser()
        val g1 = fixtures.newGroup(user)
        val g2 = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        adapter.share(g1, user, review.reviewId)
        adapter.share(g2, user, review.reviewId)

        assertEquals(2, adapter.unshareByReview(review.reviewId))
        assertTrue(adapter.findSharedGroupIds(review.reviewId).isEmpty())
    }

    @Test
    fun `탈퇴로 그 그룹의 내 공유만 내려간다`() {
        val user = fixtures.newUser()
        val leaving = fixtures.newGroup(user)
        val staying = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        adapter.share(leaving, user, review.reviewId)
        adapter.share(staying, user, review.reviewId)

        assertEquals(1, adapter.unshareAllByUser(leaving, user))
        assertEquals(0, sharedCount(leaving, user))
        assertEquals(1, sharedCount(staying, user))
    }

    private fun sharedReviewIds(
        groupId: Long,
        userId: Long,
    ): List<Long> =
        jdbcTemplate.queryForList(
            "SELECT review_id FROM group_review_share WHERE group_id = ? AND user_id = ? ORDER BY review_id",
            Long::class.java,
            groupId,
            userId,
        )

    private fun sharedCount(
        groupId: Long,
        userId: Long,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM group_review_share WHERE group_id = ? AND user_id = ?",
            Int::class.java,
            groupId,
            userId,
        )!!
}
