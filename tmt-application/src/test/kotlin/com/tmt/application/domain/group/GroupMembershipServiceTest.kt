package com.tmt.application.domain.group

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.domain.save.FakeGroupJoinTicketPort
import com.tmt.application.port.input.JoinBlockedReason
import com.tmt.application.port.input.JoinGroupCommand
import com.tmt.application.port.output.persistence.GroupJoinTarget
import com.tmt.application.port.output.persistence.GroupMembershipPort
import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupShareQueryPort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.application.port.output.persistence.PlaceReviewRows
import com.tmt.application.port.output.persistence.ReviewShareRows
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class GroupMembershipServiceTest {
    private val ownerId = 99L
    private val userId = 1L
    private val groupId = 10L

    private var target: GroupJoinTarget? = GroupJoinTarget(groupId, "성수 커피 탐험대", "seed/g.jpg", ownerId)
    private val members = mutableSetOf(ownerId)
    private val myReviewIds = mutableSetOf<Long>()
    private val shares = mutableListOf<Long>()
    private val stats = mutableListOf<String>()
    private val tickets = FakeGroupJoinTicketPort()

    /** 조건부 INSERT/UPDATE의 반환값을 흉내낸다 — 경합 테스트에서 false로 바꿔 쓴다 */
    private var joinRows = true
    private var leaveRows = true

    private val membershipPort =
        object : GroupMembershipPort {
            override fun findJoinTarget(groupId: Long) = target

            override fun join(
                groupId: Long,
                userId: Long,
                joinedAt: Instant,
            ): Boolean = joinRows.also { if (it) members += userId }

            override fun leave(
                groupId: Long,
                userId: Long,
            ): Boolean = leaveRows.also { if (it) members -= userId }
        }

    private val reviewQueryPort =
        object : GroupReviewQueryPort {
            override fun existsGroup(groupId: Long) = target != null

            override fun isMember(
                groupId: Long,
                userId: Long,
            ) = userId in members

            override fun findSharedReviewRows(
                groupId: Long,
                afterCreatedAt: Instant?,
                afterReviewId: Long?,
                viewerId: Long?,
                viewerLatitude: Double?,
                viewerLongitude: Double?,
                limit: Int,
            ) = PlaceReviewRows(emptyList(), false)
        }

    private val shareQueryPort =
        object : GroupShareQueryPort {
            override fun findMyReviewsWithShared(
                groupId: Long,
                userId: Long,
                afterCreatedAt: Instant?,
                afterReviewId: Long?,
                limit: Int,
            ) = ReviewShareRows(emptyList(), false)

            override fun countSharedByUser(
                groupId: Long,
                userId: Long,
            ) = shares.size

            override fun findNotMine(
                userId: Long,
                reviewIds: List<Long>,
            ) = reviewIds.filter { it !in myReviewIds }

            override fun findSharedReviewIds(
                groupId: Long,
                userId: Long,
            ) = shares.toList()
        }

    private val sharePort =
        object : GroupReviewSharePort {
            override fun share(
                groupId: Long,
                userId: Long,
                reviewId: Long,
            ) {
                shares += reviewId
            }

            override fun unshareAllByUser(
                groupId: Long,
                userId: Long,
            ): Int =
                shares.size.also {
                    stats += "unshare"
                    shares.clear()
                }

            override fun findSharedGroupIds(reviewId: Long) = emptyList<Long>()

            override fun unshareByReview(reviewId: Long) = 0

            override fun replaceUserShares(
                groupId: Long,
                userId: Long,
                reviewIds: List<Long>,
            ) = Unit
        }

    private val statsPort =
        object : GroupStatsPort {
            override fun addMember(groupId: Long) {
                stats += "addMember"
            }

            override fun removeMember(groupId: Long) {
                stats += "removeMember"
            }

            override fun refreshShareStats(groupId: Long) {
                stats += "refresh"
            }
        }

    private val service =
        GroupMembershipService(
            membershipPort,
            reviewQueryPort,
            tickets,
            shareQueryPort,
            sharePort,
            statsPort,
            MediaUrlResolver("https://media.example.com"),
        )

    @Test
    fun `가입 팝업 — 티켓이 있으면 joinable이고 이미지 URL이 조립된다`() {
        tickets.seed(userId, 2)

        val view = service.get(groupId, userId)

        assertTrue(view.joinable)
        assertNull(view.blockedReason)
        assertEquals(2, view.availableTicketCount)
        assertEquals(1, view.requiredTicketCount)
        assertEquals("https://media.example.com/seed/g.jpg", view.imageUrl)
    }

    @Test
    fun `가입 팝업 — 티켓이 없으면 TICKET_REQUIRED, 이미 가입이면 ALREADY_MEMBER가 우선이다 (G8)`() {
        assertEquals(JoinBlockedReason.TICKET_REQUIRED, service.get(groupId, userId).blockedReason)
        // 그룹장은 티켓이 0장이어도 ALREADY_MEMBER다
        assertEquals(JoinBlockedReason.ALREADY_MEMBER, service.get(groupId, ownerId).blockedReason)
    }

    @Test
    fun `가입하면 티켓 1장이 소비되고 멤버 수가 오른다 (TX-3)`() {
        tickets.seed(userId, 1)

        val result = service.join(JoinGroupCommand(userId, groupId, emptyList()))

        assertEquals(1, result.consumedCount)
        assertEquals(0, result.availableCount)
        assertTrue(result.sharedReviewIds.isEmpty())
        assertTrue(userId in members)
        assertEquals(listOf(userId to groupId), tickets.consumedFor)
        assertEquals(listOf("addMember"), stats)
    }

    @Test
    fun `sourceReviewIds를 보내면 가입과 함께 공유되고 집계가 다시 맞춰진다 (G9)`() {
        tickets.seed(userId, 1)
        myReviewIds += setOf(5L, 7L)

        val result = service.join(JoinGroupCommand(userId, groupId, listOf(5L, 7L, 5L)))

        assertEquals(listOf(5L, 7L), result.sharedReviewIds)
        assertEquals(listOf(5L, 7L), shares)
        assertEquals(listOf("addMember", "refresh"), stats)
    }

    @Test
    fun `타인의 리뷰가 섞여 있으면 REVIEW_NOT_FOUND이고 티켓은 차감되지 않는다`() {
        tickets.seed(userId, 1)
        myReviewIds += 5L

        val e = assertThrows<TmtException> { service.join(JoinGroupCommand(userId, groupId, listOf(5L, 8L))) }

        assertEquals(ErrorCode.REVIEW_NOT_FOUND, e.errorCode)
        assertEquals(1, tickets.countAvailable(userId))
        assertFalse(userId in members)
    }

    @Test
    fun `이미 가입은 티켓 부족보다 먼저다 (G8)`() {
        val e = assertThrows<TmtException> { service.join(JoinGroupCommand(ownerId, groupId, emptyList())) }

        assertEquals(ErrorCode.ALREADY_GROUP_MEMBER, e.errorCode)
    }

    @Test
    fun `티켓이 없으면 잔여 수를 실어 거절한다`() {
        val e = assertThrows<TicketShortageException> { service.join(JoinGroupCommand(userId, groupId, emptyList())) }

        assertEquals(ErrorCode.GROUP_JOIN_TICKET_REQUIRED, e.errorCode)
        assertEquals(0, e.availableCount)
        assertEquals(1, e.shortageCount)
        assertFalse(userId in members)
    }

    @Test
    fun `isMember를 통과했는데 삽입이 0행이면 경합에 진 것이라 ALREADY_GROUP_MEMBER다`() {
        tickets.seed(userId, 1)
        joinRows = false

        val e = assertThrows<TmtException> { service.join(JoinGroupCommand(userId, groupId, emptyList())) }

        assertEquals(ErrorCode.ALREADY_GROUP_MEMBER, e.errorCode)
        assertTrue(stats.isEmpty(), "멤버 수를 올리기 전에 끊어야 한다")
    }

    @Test
    fun `소비에 실패하면 잔여 수를 집을 수 있는 장 기준으로 알린다`() {
        // 다른 트랜잭션이 유일한 장을 잡고 있는 상황 — 잔고는 1인데 이 요청이 집을 수 있는 건 0이다.
        // countAvailable로 세면 `available: 1, shortage: 0`인 409가 나가 응답이 자기모순이 된다
        tickets.seed(userId, 1)
        tickets.consumable = 0
        tickets.consumeFails = true

        val e = assertThrows<TicketShortageException> { service.join(JoinGroupCommand(userId, groupId, emptyList())) }

        assertEquals(0, e.availableCount)
        assertEquals(1, e.shortageCount)
    }

    @Test
    fun `없는 그룹이면 GROUP_NOT_FOUND다`() {
        target = null

        assertEquals(ErrorCode.GROUP_NOT_FOUND, assertThrows<TmtException> { service.get(groupId, userId) }.errorCode)
        assertEquals(
            ErrorCode.GROUP_NOT_FOUND,
            assertThrows<TmtException> { service.join(JoinGroupCommand(userId, groupId, emptyList())) }.errorCode,
        )
        assertEquals(ErrorCode.GROUP_NOT_FOUND, assertThrows<TmtException> { service.leave(groupId, userId) }.errorCode)
    }

    @Test
    fun `탈퇴하면 공유가 전부 내려가고 집계가 다시 맞춰진다 (G10)`() {
        members += userId
        shares += listOf(5L, 7L)

        service.leave(groupId, userId)

        assertFalse(userId in members)
        assertTrue(shares.isEmpty())
        // 공유를 먼저 내리고 그룹 행을 나중에 잡는다 — 공유 집합 교체와 잠금 순서를 맞춘 것이다 (PR #99 리뷰)
        assertEquals(listOf("unshare", "removeMember", "refresh"), stats)
    }

    @Test
    fun `공유가 없던 탈퇴는 집계를 다시 세지 않는다`() {
        members += userId

        service.leave(groupId, userId)

        assertEquals(listOf("unshare", "removeMember"), stats)
    }

    @Test
    fun `그룹장은 탈퇴할 수 없다 (G11)`() {
        val e = assertThrows<TmtException> { service.leave(groupId, ownerId) }

        assertEquals(ErrorCode.GROUP_OWNER_CANNOT_LEAVE, e.errorCode)
        assertTrue(ownerId in members)
    }

    @Test
    fun `가입하지 않은 그룹의 탈퇴는 GROUP_MEMBERSHIP_REQUIRED다`() {
        val e = assertThrows<TmtException> { service.leave(groupId, userId) }

        assertEquals(ErrorCode.GROUP_MEMBERSHIP_REQUIRED, e.errorCode)
    }

    @Test
    fun `isMember를 통과했는데 전이가 0행이면 경합에 진 것이라 GROUP_MEMBERSHIP_REQUIRED다`() {
        members += userId
        leaveRows = false

        val e = assertThrows<TmtException> { service.leave(groupId, userId) }

        assertEquals(ErrorCode.GROUP_MEMBERSHIP_REQUIRED, e.errorCode)
        assertTrue(stats.isEmpty())
    }
}
