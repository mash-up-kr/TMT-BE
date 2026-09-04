package com.tmt.application.domain.group

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.GetJoinPreviewUseCase
import com.tmt.application.port.input.JoinBlockedReason
import com.tmt.application.port.input.JoinGroupCommand
import com.tmt.application.port.input.JoinGroupResult
import com.tmt.application.port.input.JoinGroupUseCase
import com.tmt.application.port.input.JoinPreviewView
import com.tmt.application.port.input.LeaveGroupUseCase
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.GroupMembershipPort
import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupShareQueryPort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 그룹 가입·탈퇴 (H §2·§3-3, TMT-227). */
@Service
class GroupMembershipService(
    private val groupMembershipPort: GroupMembershipPort,
    private val groupReviewQueryPort: GroupReviewQueryPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
    private val groupShareQueryPort: GroupShareQueryPort,
    private val groupReviewSharePort: GroupReviewSharePort,
    private val groupStatsPort: GroupStatsPort,
    private val mediaUrlResolver: MediaUrlResolver,
) : GetJoinPreviewUseCase,
    JoinGroupUseCase,
    LeaveGroupUseCase {
    @Transactional(readOnly = true)
    override fun get(
        groupId: Long,
        userId: Long,
    ): JoinPreviewView {
        val target = groupMembershipPort.findJoinTarget(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        val available = groupJoinTicketPort.countAvailable(userId)
        val blockedReason =
            when {
                groupReviewQueryPort.isMember(groupId, userId) -> JoinBlockedReason.ALREADY_MEMBER
                available < REQUIRED_TICKETS -> JoinBlockedReason.TICKET_REQUIRED
                else -> null
            }
        return JoinPreviewView(
            groupId = groupId,
            name = target.name,
            imageUrl = target.imageS3Key?.let(mediaUrlResolver::urlOf),
            availableTicketCount = available,
            requiredTicketCount = REQUIRED_TICKETS,
            blockedReason = blockedReason,
        )
    }

    /**
     * 순서가 곧 규칙이다 — 이미 가입(G8) → 공유 대상 검증 → 티켓 소비 → 멤버십 → 공유.
     * 공유 대상이 잘못됐을 때 티켓이 먼저 차감되면 롤백돼도 화면에는 부족으로 보일 수 있다 (TX-3).
     */
    @Transactional
    override fun join(command: JoinGroupCommand): JoinGroupResult {
        val groupId = command.groupId
        val userId = command.userId
        groupMembershipPort.findJoinTarget(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        if (groupReviewQueryPort.isMember(groupId, userId)) throw TmtException(ErrorCode.ALREADY_GROUP_MEMBER)

        val reviewIds = command.sourceReviewIds.distinct()
        groupShareQueryPort.findNotMine(userId, reviewIds).firstOrNull()?.let {
            throw TmtException(ErrorCode.REVIEW_NOT_FOUND, "rv_$it")
        }

        if (!groupJoinTicketPort.consumeOne(userId, groupId)) {
            // countAvailable로 세면 안 된다 — 소비 실패의 절반은 "다른 트랜잭션이 잡은 장을 건너뛴 것"인데
            // READ COMMITTED라 그 장이 아직 AVAILABLE로 보인다. 응답이 `available: 1, shortage: 0`인
            // 409가 되어 화면이 "티켓 있는데 실패"를 받는다 (PR #99 리뷰)
            throw TicketShortageException(
                errorCode = ErrorCode.GROUP_JOIN_TICKET_REQUIRED,
                availableCount = groupJoinTicketPort.countConsumable(userId),
                requiredCount = REQUIRED_TICKETS,
            )
        }

        val joinedAt = Instant.now()
        // 위 isMember를 통과했는데 0행이면 그 사이 다른 요청이 가입한 것이다 — 롤백으로 티켓이 돌아온다
        if (!groupMembershipPort.join(groupId, userId, joinedAt)) throw TmtException(ErrorCode.ALREADY_GROUP_MEMBER)
        groupStatsPort.addMember(groupId)

        reviewIds.forEach { groupReviewSharePort.share(groupId, userId, it) }
        if (reviewIds.isNotEmpty()) groupStatsPort.refreshShareStats(groupId)

        return JoinGroupResult(
            groupId = groupId,
            joinedAt = joinedAt,
            sharedReviewIds = reviewIds,
            consumedCount = REQUIRED_TICKETS,
            availableCount = groupJoinTicketPort.countAvailable(userId),
        )
    }

    @Transactional
    override fun leave(
        groupId: Long,
        userId: Long,
    ) {
        val target = groupMembershipPort.findJoinTarget(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupReviewQueryPort.isMember(groupId, userId)) throw TmtException(ErrorCode.GROUP_MEMBERSHIP_REQUIRED)
        if (target.ownerId == userId) throw TmtException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE)

        if (!groupMembershipPort.leave(groupId, userId)) throw TmtException(ErrorCode.GROUP_MEMBERSHIP_REQUIRED)

        // 공유를 먼저 내리고 그룹 행을 나중에 잡는다 — 공유 집합 교체(TX-4)가 `공유 → lockGroup` 순서라
        // 반대로 두면 두 요청이 서로의 자원을 기다린다. 한 트랜잭션이라 순서가 결과를 바꾸지는 않는다
        val unshared = groupReviewSharePort.unshareAllByUser(groupId, userId)
        groupStatsPort.removeMember(groupId)
        if (unshared > 0) groupStatsPort.refreshShareStats(groupId)
    }

    companion object {
        /** 그룹마다 다르지 않다 (T3) */
        private const val REQUIRED_TICKETS = 1
    }
}
