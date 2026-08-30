package com.tmt.application.domain.review

import com.tmt.application.port.input.DeleteReviewUseCase
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.ReviewCommandPort
import com.tmt.application.port.output.persistence.ReviewQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리뷰 삭제 (I §6-4, TMT-226). 삭제는 작성 때 나간 것을 되돌리는 일이라 한 트랜잭션에 묶인다
 * (TX-5) — 티켓 회수, 그룹 공유 해제와 그룹 지표, 매장 집계, 사진 행 삭제가 전부 같이 커밋되거나
 * 전부 안 된다.
 *
 * **티켓 회수를 맨 앞에 둔다.** 회수할 게 없으면 아무것도 되돌리지 않고 거절해야 한다 —
 * 이미 그룹 가입에 쓴 티켓은 돌아오지 않으므로 삭제 자체를 막는 것이 규칙이다 (R7).
 */
@Service
class ReviewDeletionService(
    private val reviewQueryPort: ReviewQueryPort,
    private val reviewCommandPort: ReviewCommandPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
    private val groupReviewSharePort: GroupReviewSharePort,
    private val groupStatsPort: GroupStatsPort,
    private val placeStatsPort: PlaceStatsPort,
    private val mediaAssetPort: MediaAssetPort,
    private val eventPublisher: ApplicationEventPublisher,
) : DeleteReviewUseCase {
    @Transactional
    override fun delete(
        userId: Long,
        reviewId: Long,
    ) {
        val review = reviewQueryPort.findReviewForDeletion(reviewId)
        // 타인의 리뷰도 없는 리뷰와 같게 404다 — 존재 여부를 새지 않는다 (규약 §3-2)
        if (review == null || review.userId != userId) throw TmtException(ErrorCode.REVIEW_NOT_FOUND)

        if (!groupJoinTicketPort.revokeOneForReview(userId, reviewId)) {
            throw TicketShortageException(
                errorCode = ErrorCode.REVIEW_DELETE_TICKET_REQUIRED,
                availableCount = groupJoinTicketPort.countAvailable(userId),
            )
        }

        // 대상 그룹은 내리기 전에 잡아둔다 — 내린 뒤에는 어느 그룹이었는지 알 수 없다
        val sharedGroupIds = groupReviewSharePort.findSharedGroupIds(reviewId)
        groupReviewSharePort.unshareByReview(reviewId)
        sharedGroupIds.forEach(groupStatsPort::refreshShareStats)

        placeStatsPort.removeReview(review.placeId, review.rating)

        // 사진은 저장으로 되돌아가지 않고 완전히 사라진다 (R6) — save_photo를 먼저 지워야
        // media_asset을 지울 수 있다 (스키마에 CASCADE가 없다)
        val assetIds = reviewCommandPort.deletePhotoLinks(review.saveId)
        val s3Keys = mediaAssetPort.findByIds(assetIds).map { it.s3Key }
        mediaAssetPort.deleteByIds(assetIds)

        reviewCommandPort.softDeleteReview(reviewId)
        reviewCommandPort.softDeleteSave(review.saveId)

        // S3는 커밋 후에 지운다. 먼저 지우고 트랜잭션이 깨지면 살아 있는 리뷰의 사진이 사라진다 —
        // 반대로 커밋 후 실패하면 아무도 참조하지 않는 객체만 남는다
        if (s3Keys.isNotEmpty()) eventPublisher.publishEvent(ReviewPhotosDeletedEvent(reviewId, s3Keys))
    }
}
