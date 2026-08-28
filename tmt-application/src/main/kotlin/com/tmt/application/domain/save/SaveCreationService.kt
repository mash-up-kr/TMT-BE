package com.tmt.application.domain.save

import com.tmt.application.domain.aisummary.ReviewCommittedEvent
import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.domain.place.PlaceRules
import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.PlaceSelection
import com.tmt.application.port.input.SaveResult
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.NewPlaceRow
import com.tmt.application.port.output.persistence.PlaceCommandPort
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.ReviewTagPort
import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 작성 완료 (F §4-1, TMT-224·TMT-193). 한 번의 호출로 (필요하면 Place와) Save가 생기고,
 * 완성도 판정(C4)을 통과하면 Review·RewardGrant·GroupJoinTicket과 매장 집계까지 같은
 * 트랜잭션에서 나간다 (TX-1).
 *
 * 트랜잭션은 멱등 처리(IdempotentRequestTransaction)가 이미 열어 두고, 여기서는 그 경계에
 * 참여한다 — 응답 기록과 비즈니스 쓰기가 같이 커밋돼야 "티켓은 나갔는데 기록은 없음"이 안 생긴다.
 *
 * addressId 서명 검증과 좌표 API 호출은 이 경계 밖에서 끝나 있다 — 커맨드의
 * [PlaceSelection.New]는 이미 해석된 값이다.
 */
@Service
class SaveCreationService(
    private val saveCommandPort: SaveCommandPort,
    private val placeQueryPort: PlaceQueryPort,
    private val placeCommandPort: PlaceCommandPort,
    private val reviewTagPort: ReviewTagPort,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val groupJoinTicketPort: GroupJoinTicketPort,
    private val placeStatsPort: PlaceStatsPort,
    private val eventPublisher: ApplicationEventPublisher,
) : CreateSaveUseCase {
    @Transactional
    override fun create(command: CreateSaveCommand): SaveResult {
        validate(command)

        // 직접 등록은 판정과 무관하게 항상 매장을 만든다 — 사용자가 `작성 완료`를 눌렀으므로
        // C3 위반이 아니고, 미완성이면 이어쓰기로 채울 때 리뷰가 붙는다
        val placeId = resolvePlace(command.place)

        val saveId =
            saveCommandPort.insertSave(
                userId = command.userId,
                placeId = placeId,
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
                placeId = placeId,
                grantedCount = 0,
                availableCount = groupJoinTicketPort.countAvailable(command.userId),
            )
        }

        val reviewId = saveCommandPort.insertReview(saveId, command.userId, placeId)
        val granted = tryGrantTicket(command.userId, reviewId)
        placeStatsPort.addReview(placeId, requireNotNull(command.rating))
        // 커밋 후 비동기로 요약을 당겨 채운다 (TMT-232). 유실분은 주기 배치가 줍는다
        eventPublisher.publishEvent(ReviewCommittedEvent(reviewId = reviewId, placeId = placeId))

        return SaveResult(
            saveId = saveId,
            reviewId = reviewId,
            placeId = placeId,
            grantedCount = granted,
            availableCount = groupJoinTicketPort.countAvailable(command.userId),
        )
    }

    /**
     * 기존 매장이면 존재만 확인하고, 직접 등록이면 새 Place를 만든다.
     *
     * 서버는 매장 병합을 하지 않는다 — 좌표는 건물 단위라 한 상가의 모든 가게가 같고, 매장명
     * 유사도는 임계값이 임의적이라 판정할 수단이 없다 (F §4-1). 같은 가게가 복수 Place로 남는 것은
     * 의도한 트레이드오프다.
     */
    private fun resolvePlace(selection: PlaceSelection): Long =
        when (selection) {
            is PlaceSelection.Existing -> {
                if (!placeQueryPort.existsPlace(selection.placeId)) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
                selection.placeId
            }

            is PlaceSelection.New ->
                placeCommandPort.insertManualPlace(
                    NewPlaceRow(
                        // UNIQUE (external_source, external_id)가 재시도 중복을 막지 못한다 — 값이 매번 다르다
                        externalId = UUID.randomUUID().toString(),
                        name = selection.name,
                        roadAddress = selection.roadAddress,
                        jibunAddress = selection.jibunAddress,
                        regionName = selection.regionName,
                        categoryId = selection.categoryId,
                        latitude = selection.latitude,
                        longitude = selection.longitude,
                    ),
                )
        }

    /** 보유 999장 미만일 때만 발급한다 (T6). 상한이면 리뷰는 성립하고 티켓만 안 나간다. */
    private fun tryGrantTicket(
        userId: Long,
        reviewId: Long,
    ): Int {
        if (groupJoinTicketPort.countAvailable(userId) >= SaveRules.TICKET_MAX_AVAILABLE) return 0
        groupJoinTicketPort.grantForReview(userId, reviewId)
        return 1
    }

    private fun validate(command: CreateSaveCommand) {
        (command.place as? PlaceSelection.New)?.let(::validateNewPlace)
        if (command.photoAssetIds.size > SaveRules.PHOTO_MAX_COUNT) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "사진은 최대 ${SaveRules.PHOTO_MAX_COUNT}장입니다.")
        }
        // save_photo.media_asset_id UNIQUE가 거부하는 자리를 미리 막는다
        if (command.photoAssetIds.distinct().size != command.photoAssetIds.size) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "photoAssetIds에 같은 사진이 두 번 들어 있습니다.")
        }
        attachMediaUseCase.verifyAttachable(ownerId = command.userId, assetIds = command.photoAssetIds)

        verifyTags(command.companionTagIds, companion = true)
        verifyTags(command.positivePointTagIds, companion = false)

        command.rating?.let {
            if (it !in SaveRules.RATING_MIN..SaveRules.RATING_MAX) {
                throw TmtException(ErrorCode.VALIDATION_FAILED, "rating은 1~5 정수입니다.")
            }
        }
        command.content?.let {
            if (it.length > SaveRules.CONTENT_MAX_LENGTH) throw TmtException(ErrorCode.REVIEW_CONTENT_TOO_LONG)
        }
    }

    /** 이름은 요청값이고 주소·지역명은 addressId 토큰에서 온다 — 컬럼 폭을 넘으면 INSERT가 깨진다 (F §7). */
    private fun validateNewPlace(place: PlaceSelection.New) {
        if (place.name.isEmpty() || place.name.length > PlaceRules.NAME_MAX_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "매장명은 1~${PlaceRules.NAME_MAX_LENGTH}자입니다.")
        }
        if (place.regionName.length > PlaceRules.REGION_NAME_MAX_LENGTH ||
            place.roadAddress.length > PlaceRules.ROAD_ADDRESS_MAX_LENGTH
        ) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "주소 표기가 저장할 수 있는 길이를 넘습니다.")
        }
        place.categoryId?.let {
            if (it !in FoodCategories.LABEL_BY_ID) throw TmtException(ErrorCode.PLACE_CATEGORY_NOT_FOUND, it)
        }
    }

    /** 분류가 어긋난 태그도 없는 태그와 같게 본다 — 좋은 점 자리에 동행 태그가 들어오면 판정이 깨진다. */
    private fun verifyTags(
        tagIds: List<String>,
        companion: Boolean,
    ) {
        if (tagIds.isEmpty()) return
        val known =
            reviewTagPort
                .findActiveTags(tagIds.distinct())
                .filter { it.companion == companion }
                .map { it.tagId }
                .toSet()
        tagIds.firstOrNull { it !in known }?.let { throw TmtException(ErrorCode.REVIEW_TAG_NOT_FOUND, it) }
    }
}
