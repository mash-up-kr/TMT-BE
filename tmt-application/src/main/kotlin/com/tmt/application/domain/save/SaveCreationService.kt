package com.tmt.application.domain.save

import com.tmt.application.domain.aisummary.ReviewCommittedEvent
import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.SaveResult
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 작성 완료 (F §4-1, TMT-224). 한 번의 호출로 Save가 생기고, 완성도 판정(C4)을 통과하면
 * Review·RewardGrant·GroupJoinTicket과 매장 집계까지 같은 트랜잭션에서 나간다 (TX-1).
 *
 * 트랜잭션은 멱등 처리(IdempotentRequestTransaction)가 이미 열어 두고, 여기서는 그 경계에
 * 참여한다 — 응답 기록과 비즈니스 쓰기가 같이 커밋돼야 "티켓은 나갔는데 기록은 없음"이 안 생긴다.
 */
@Service
class SaveCreationService(
    private val saveCommandPort: SaveCommandPort,
    private val placeQueryPort: PlaceQueryPort,
    private val saveWriteSupport: SaveWriteSupport,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val placeStatsPort: PlaceStatsPort,
    private val eventPublisher: ApplicationEventPublisher,
) : CreateSaveUseCase {
    @Transactional
    override fun create(command: CreateSaveCommand): SaveResult {
        if (!placeQueryPort.existsPlace(command.placeId)) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        saveWriteSupport.validate(
            userId = command.userId,
            photoAssetIds = command.photoAssetIds,
            companionTagIds = command.companionTagIds,
            positivePointTagIds = command.positivePointTagIds,
            rating = command.rating,
            content = command.content,
        )

        val saveId =
            saveCommandPort.insertSave(
                userId = command.userId,
                placeId = command.placeId,
                rating = command.rating,
                content = command.content,
            )
        saveCommandPort.insertPhotos(saveId, command.photoAssetIds)
        saveCommandPort.insertTags(saveId, (command.companionTagIds + command.positivePointTagIds).distinct())
        attachMediaUseCase.attach(command.photoAssetIds)

        val completed =
            SaveRules.satisfiesReviewCriteria(
                photoCount = command.photoAssetIds.size,
                companionTagCount = command.companionTagIds.size,
                positivePointTagCount = command.positivePointTagIds.size,
                rating = command.rating,
                content = command.content,
            )
        if (!completed) {
            // 집계는 판정을 통과했을 때만 움직인다 — 여기서 돌리면 rating이 null인 저장까지
            // review_count를 올려 매장 평균(P9)과 지도 핀 조건(E6)이 함께 틀어진다
            return SaveResult(
                saveId = saveId,
                reviewId = null,
                placeId = command.placeId,
                grantedCount = 0,
                availableCount = saveWriteSupport.availableTicketCount(command.userId),
            )
        }

        val reviewId = saveCommandPort.insertReview(saveId, command.userId, command.placeId)
        val granted = saveWriteSupport.tryGrantTicket(command.userId, reviewId)
        placeStatsPort.addReview(command.placeId, requireNotNull(command.rating))
        // 커밋 후 비동기로 요약을 당겨 채운다 (TMT-232). 유실분은 주기 배치가 줍는다
        eventPublisher.publishEvent(ReviewCommittedEvent(reviewId = reviewId, placeId = command.placeId))

        return SaveResult(
            saveId = saveId,
            reviewId = reviewId,
            placeId = command.placeId,
            grantedCount = granted,
            availableCount = saveWriteSupport.availableTicketCount(command.userId),
        )
    }
}
