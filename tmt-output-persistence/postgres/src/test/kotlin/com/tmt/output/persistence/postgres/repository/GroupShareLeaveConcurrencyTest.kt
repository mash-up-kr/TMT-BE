package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.InterleavedTransactions
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager

/**
 * 탈퇴와 공유 교체의 경합 (G10, TMT-351).
 *
 * 문장 순서는 서비스와 같다 — 교체(`GroupShareService.replace`)는 멤버십 잠금 → 공유 쓰기,
 * 탈퇴(`GroupMembershipService.leave`)는 멤버십 전이 → 공유 삭제. 두 흐름이 멤버십 행을
 * 함께 잠가 서로를 기다린다 — 잠그지 않으면 isMember 통과 직후 탈퇴가 커밋해
 * LEFT인데 공유가 남는다.
 */
class GroupShareLeaveConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var membershipRepository: GroupMembershipRepository

    @Autowired
    private lateinit var shareRepository: GroupReviewShareRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val interleaved by lazy { InterleavedTransactions(transactionManager, jdbcTemplate) }

    @Test
    fun `교체가 멤버십을 잠근 채면 탈퇴가 기다렸다가 새 공유까지 내린다`() {
        val (groupId, userId, reviewId) = memberWithReview()

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = {
                    check(membershipRepository.lockActiveMembership(groupId, userId) != null) { "교체가 멤버십을 잠가야 한다" }
                    shareRepository.share(groupId, reviewId, userId) > 0
                },
                follower = {
                    val left = membershipRepository.leave(groupId, userId) > 0
                    shareRepository.deleteByGroupIdAndUserId(groupId, userId)
                    left
                },
            )

        assertTrue(result.followerBlocked, "탈퇴가 교체의 멤버십 잠금을 기다리지 않았다 — 경합이 재현되지 않았다")
        assertTrue(result.leaderResult, "교체가 공유를 넣어야 한다")
        assertTrue(result.followerResult, "탈퇴는 교체 커밋 후 전이돼야 한다")
        assertEquals("LEFT", membershipStatus(groupId, userId))
        assertEquals(0, shareCount(groupId, userId), "탈퇴가 나중이면 교체가 넣은 공유까지 내려가야 한다 (G10)")
    }

    @Test
    fun `탈퇴가 먼저면 교체의 멤버십 잠금이 기다렸다가 LEFT를 보고 거절된다`() {
        val (groupId, userId, _) = memberWithReview()

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = {
                    val left = membershipRepository.leave(groupId, userId) > 0
                    shareRepository.deleteByGroupIdAndUserId(groupId, userId)
                    left
                },
                follower = { membershipRepository.lockActiveMembership(groupId, userId) != null },
            )

        assertTrue(result.followerBlocked, "교체가 탈퇴의 멤버십 행 잠금을 기다리지 않았다 — 경합이 재현되지 않았다")
        assertTrue(result.leaderResult)
        assertFalse(result.followerResult, "탈퇴 커밋 후에는 LEFT라 교체가 GROUP_MEMBERSHIP_REQUIRED로 거절돼야 한다")
        assertEquals("LEFT", membershipStatus(groupId, userId))
        assertEquals(0, shareCount(groupId, userId))
    }

    /** ACTIVE 멤버 + 그 멤버가 쓴 완성 리뷰 1건. */
    private fun memberWithReview(): Triple<Long, Long, Long> {
        val userId = fixtures.newUser()
        val groupId = fixtures.newGroup(fixtures.newUser())
        fixtures.newMembership(groupId, userId)
        val placeId = fixtures.newPlace()
        val reviewId = fixtures.newReview(fixtures.newSave(userId, placeId), userId, placeId)
        return Triple(groupId, userId, reviewId)
    }

    private fun membershipStatus(
        groupId: Long,
        userId: Long,
    ): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM group_membership WHERE group_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
            String::class.java,
            groupId,
            userId,
        )!!

    private fun shareCount(
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
