package com.tmt.application.domain.group

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.GetGroupDetailUseCase
import com.tmt.application.port.input.GetGroupReviewsUseCase
import com.tmt.application.port.input.GroupDetailView
import com.tmt.application.port.input.GroupReviewsRequest
import com.tmt.application.port.input.GroupReviewsResult
import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹 상세·리뷰 목록 (D_02 §3, TMT-222). */
@Service
@Transactional(readOnly = true)
class GroupQueryService(
    private val groupReviewQueryPort: GroupReviewQueryPort,
    private val groupDetailComposer: GroupDetailComposer,
    private val reviewCardComposer: ReviewCardComposer,
) : GetGroupDetailUseCase,
    GetGroupReviewsUseCase {
    override fun get(
        groupId: Long,
        viewerId: Long?,
    ): GroupDetailView = groupDetailComposer.compose(groupId, viewerId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

    override fun get(request: GroupReviewsRequest): GroupReviewsResult {
        if (!groupReviewQueryPort.existsGroup(request.groupId)) throw TmtException(ErrorCode.GROUP_NOT_FOUND)

        // 미가입도 같은 커서 경로를 탄다 — 가리는 것은 카드의 비공개 필드뿐이다 (G1, TMT-216)
        val gated = request.viewerId == null || !groupReviewQueryPort.isMember(request.groupId, request.viewerId)

        val slice =
            groupReviewQueryPort.findSharedReviewRows(
                groupId = request.groupId,
                afterCreatedAt = request.after?.createdAt,
                afterReviewId = request.after?.reviewId,
                viewerId = request.viewerId,
                viewerLatitude = request.viewerLatitude,
                viewerLongitude = request.viewerLongitude,
                limit = request.limit,
            )
        return GroupReviewsResult(
            items = reviewCardComposer.compose(slice.rows),
            gated = gated,
            hasNext = slice.hasNext,
        )
    }
}
