package com.tmt.application.domain.save

import com.tmt.application.domain.aisummary.ReviewCommittedEvent
import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.DeleteSaveUseCase
import com.tmt.application.port.input.SaveResult
import com.tmt.application.port.input.UpdateSaveCommand
import com.tmt.application.port.input.UpdateSaveUseCase
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.application.port.output.persistence.SaveQueryPort
import com.tmt.application.port.output.persistence.SaveRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이어쓰기(G §5)와 임시저장 버리기(F·G·I §5-2). 둘 다 `reviewId IS NULL`일 때만 움직인다 (S4) —
 * 리뷰가 된 저장의 수정·삭제는 리뷰 경로 소관이고, 그쪽만 티켓 회수 규칙(R6·R7)을 지킨다.
 */
@Service
class SaveUpdateService(
    private val saveQueryPort: SaveQueryPort,
    private val saveCommandPort: SaveCommandPort,
    private val saveWriteSupport: SaveWriteSupport,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val placeStatsPort: PlaceStatsPort,
    private val eventPublisher: ApplicationEventPublisher,
) : UpdateSaveUseCase,
    DeleteSaveUseCase {
    @Transactional
    override fun update(command: UpdateSaveCommand): SaveResult {
        val save = findDraft(command.saveId, command.userId)
        // 매장은 바꿀 수 없다 (S6). 읽을 수 없는 placeId도 "다른 매장"과 같게 본다
        if (command.newPlaceRequested || command.placeId != save.placeId) {
            throw TmtException(ErrorCode.SAVE_PLACE_IMMUTABLE)
        }

        val existingAssetIds = saveQueryPort.findPhotoAssetIds(command.saveId)
        saveWriteSupport.validate(
            userId = command.userId,
            photoAssetIds = command.photoAssetIds,
            companionTagIds = command.companionTagIds,
            positivePointTagIds = command.positivePointTagIds,
            rating = command.rating,
            content = command.content,
            reattachableIds = existingAssetIds.toSet(),
        )

        saveCommandPort.updateSave(command.saveId, command.rating, command.content)
        // 전체 교체다 — 남은 save_photo 행이 있으면 media_asset_id UNIQUE가 재부착을 막는다
        saveCommandPort.deletePhotos(command.saveId)
        saveCommandPort.insertPhotos(command.saveId, command.photoAssetIds)
        saveCommandPort.deleteTags(command.saveId)
        saveCommandPort.insertTags(
            command.saveId,
            (command.companionTagIds + command.positivePointTagIds).distinct(),
        )

        val keptAssetIds = existingAssetIds.intersect(command.photoAssetIds.toSet())
        attachMediaUseCase.detach(existingAssetIds - command.photoAssetIds.toSet())
        attachMediaUseCase.attach(command.photoAssetIds, reattachableIds = keptAssetIds)

        val completed =
            SaveRules.satisfiesReviewCriteria(
                photoCount = command.photoAssetIds.size,
                companionTagCount = command.companionTagIds.size,
                positivePointTagCount = command.positivePointTagIds.size,
                rating = command.rating,
                content = command.content,
            )
        if (!completed) {
            return SaveResult(
                saveId = command.saveId,
                reviewId = null,
                placeId = save.placeId,
                grantedCount = 0,
                availableCount = saveWriteSupport.availableTicketCount(command.userId),
            )
        }

        // 판정이 이번에 충족됐다 — 리뷰·티켓·집계가 이 시점에 생긴다 (C6)
        val reviewId = saveCommandPort.insertReview(command.saveId, command.userId, save.placeId)
        val granted = saveWriteSupport.tryGrantTicket(command.userId, reviewId)
        placeStatsPort.addReview(save.placeId, requireNotNull(command.rating))
        eventPublisher.publishEvent(ReviewCommittedEvent(reviewId = reviewId, placeId = save.placeId))

        return SaveResult(
            saveId = command.saveId,
            reviewId = reviewId,
            placeId = save.placeId,
            grantedCount = granted,
            availableCount = saveWriteSupport.availableTicketCount(command.userId),
        )
    }

    @Transactional
    override fun delete(
        userId: Long,
        saveId: Long,
    ) {
        findDraft(saveId, userId)

        val assetIds = saveQueryPort.findPhotoAssetIds(saveId)
        saveCommandPort.deletePhotos(saveId)
        saveCommandPort.deleteTags(saveId)
        saveCommandPort.softDeleteSave(saveId)
        // 사진은 지우지 않고 STAGED로 되돌린다 — 미부착 TTL이 정리한다 (M4)
        attachMediaUseCase.detach(assetIds)
    }

    /** 소유자에게만 응답한다 (S8). */
    private fun findDraft(
        saveId: Long,
        userId: Long,
    ): SaveRow {
        val save = saveQueryPort.findSave(saveId) ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)
        if (save.userId != userId) throw TmtException(ErrorCode.FORBIDDEN)
        if (save.reviewId != null) throw TmtException(ErrorCode.SAVE_ALREADY_REVIEWED)
        return save
    }
}
