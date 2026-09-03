package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.InterleavedTransactions
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 같은 그룹에 같은 사용자가 동시에 가입하면 `membership_active_uq`(부분 인덱스)가 심판이다 (D5).
 * 조회로 가르면 둘 다 통과하므로 ON CONFLICT의 삽입 건수로 판정한다.
 */
class GroupMembershipRepositoryConcurrencyTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: GroupMembershipRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val interleaved by lazy { InterleavedTransactions(transactionManager, jdbcTemplate) }
    private val transaction by lazy { TransactionTemplate(transactionManager) }

    @Test
    fun `같은 그룹에 동시에 가입하면 한 건만 들어간다`() {
        val userId = fixtures.newUser()
        val groupId = fixtures.newGroup(fixtures.newUser())

        val result =
            interleaved.followerEntersBeforeCommit(
                leader = { repository.insertIfAbsent(groupId, userId, Instant.now()) },
                follower = { repository.insertIfAbsent(groupId, userId, Instant.now()) },
            )

        assertTrue(result.followerBlocked, "후행이 선행의 커밋을 기다리지 않았다 — 경합이 재현되지 않았다")
        assertEquals(1, result.leaderResult)
        assertEquals(0, result.followerResult)
        assertEquals(1, activeCount(groupId, userId))
    }

    @Test
    fun `탈퇴한 뒤 다시 가입하면 새 ACTIVE 행이 생긴다 — LEFT 행은 유일성에 걸리지 않는다`() {
        val userId = fixtures.newUser()
        val groupId = fixtures.newGroup(fixtures.newUser())
        fixtures.joinGroup(groupId, userId)

        assertEquals(1, transaction.execute { repository.leave(groupId, userId) })
        assertEquals(0, transaction.execute { repository.leave(groupId, userId) }, "두 번째 탈퇴는 0행이다")
        assertEquals(1, transaction.execute { repository.insertIfAbsent(groupId, userId, Instant.now()) })

        assertEquals(1, activeCount(groupId, userId))
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_membership WHERE group_id = ? AND user_id = ?",
                Int::class.java,
                groupId,
                userId,
            ),
        )
    }

    @Test
    fun `가입 대상 조회는 대표 이미지 s3_key와 그룹장을 함께 준다`() {
        val ownerId = fixtures.newUser()
        val groupId = fixtures.newGroup(ownerId)

        val target = repository.findJoinTarget(groupId)!!

        assertEquals(groupId, target.getGroupId())
        assertEquals(ownerId, target.getOwnerId())
        assertEquals(null, target.getImageS3Key())
    }

    private fun activeCount(
        groupId: Long,
        userId: Long,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM group_membership WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'",
            Int::class.java,
            groupId,
            userId,
        )!!
}
