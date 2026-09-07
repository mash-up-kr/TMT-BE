package com.tmt.application.domain.group

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.GetReviewSharesUseCase
import com.tmt.application.port.input.ReplaceReviewSharesUseCase
import com.tmt.application.port.input.ReplaceSharesResult
import com.tmt.application.port.input.ReviewShareItemView
import com.tmt.application.port.input.ReviewSharesRequest
import com.tmt.application.port.input.ReviewSharesResult
import com.tmt.application.port.output.persistence.GroupMembershipPort
import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupShareQueryPort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 리뷰 공유 집합 (H §3, TMT-223). */
@Service
class GroupShareService(
    private val groupShareQueryPort: GroupShareQueryPort,
    private val groupReviewQueryPort: GroupReviewQueryPort,
    private val groupMembershipPort: GroupMembershipPort,
    private val groupReviewSharePort: GroupReviewSharePort,
    private val groupStatsPort: GroupStatsPort,
    private val mediaUrlResolver: MediaUrlResolver,
) : GetReviewSharesUseCase,
    ReplaceReviewSharesUseCase {
    @Transactional(readOnly = true)
    override fun get(request: ReviewSharesRequest): ReviewSharesResult {
        if (!groupReviewQueryPort.existsGroup(request.groupId)) throw TmtException(ErrorCode.GROUP_NOT_FOUND)

        val slice =
            groupShareQueryPort.findMyReviewsWithShared(
                groupId = request.groupId,
                userId = request.userId,
                afterCreatedAt = request.after?.createdAt,
                afterReviewId = request.after?.reviewId,
                limit = request.limit,
            )
        return ReviewSharesResult(
            items =
                slice.rows.map {
                    ReviewShareItemView(
                        reviewId = it.reviewId,
                        placeName = it.placeName,
                        thumbnailUrl = mediaUrlResolver.urlOf(it.thumbnailS3Key),
                        contentPreview = it.content,
                        isShared = it.isShared,
                        createdAt = it.createdAt,
                    )
                },
            sharedCount = groupShareQueryPort.countSharedByUser(request.groupId, request.userId),
            hasNext = slice.hasNext,
        )
    }

    /**
     * 교체와 집계 반영이 한 트랜잭션이다 (TX-4) — 중간에 끊기면 지표가 공유 집합과 어긋난다.
     *
     * 멤버십은 조회가 아니라 **행 잠금으로** 확인한다 (TMT-351) — 잠그지 않으면 확인 직후 탈퇴가
     * 커밋해도 공유 INSERT가 막히지 않아, LEFT인데 공유가 남는다 (G10). 탈퇴가 잠금을 쥐고 있으면
     * 여기서 기다렸다 LEFT를 보고 거절되고, 이쪽이 먼저면 탈퇴가 기다렸다 새 공유까지 내린다.
     */
    @Transactional
    override fun replace(
        groupId: Long,
        userId: Long,
        reviewIds: List<Long>,
    ): ReplaceSharesResult {
        if (!groupReviewQueryPort.existsGroup(groupId)) throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupMembershipPort.lockActiveMembership(groupId, userId)) {
            throw TmtException(ErrorCode.GROUP_MEMBERSHIP_REQUIRED)
        }

        val distinct = reviewIds.distinct()
        groupShareQueryPort.findNotMine(userId, distinct).firstOrNull()?.let {
            throw TmtException(ErrorCode.REVIEW_NOT_FOUND, "rv_$it")
        }

        groupReviewSharePort.replaceUserShares(groupId, userId, distinct)
        groupStatsPort.refreshShareStats(groupId)

        val shared = groupShareQueryPort.findSharedReviewIds(groupId, userId)
        return ReplaceSharesResult(sharedReviewIds = shared, sharedCount = shared.size)
    }
}
