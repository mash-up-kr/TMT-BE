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
            throw TicketShortageException(
                errorCode = ErrorCode.GROUP_JOIN_TICKET_REQUIRED,
                availableCount = groupJoinTicketPort.countAvailable(userId),
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
        groupStatsPort.removeMember(groupId)

        if (groupReviewSharePort.unshareAllByUser(groupId, userId) > 0) {
            groupStatsPort.refreshShareStats(groupId)
        }
    }

    companion object {
        /** 그룹마다 다르지 않다 (T3) */
        private const val REQUIRED_TICKETS = 1
    }
}
