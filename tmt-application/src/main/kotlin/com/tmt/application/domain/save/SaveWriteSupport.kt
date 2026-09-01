package com.tmt.application.domain.save

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.ReviewTagPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Component

/** 작성 완료(F §4-1)와 이어쓰기(G §5)가 같이 쓰는 요청 검증·티켓 발급. */
@Component
class SaveWriteSupport(
    private val reviewTagPort: ReviewTagPort,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val groupJoinTicketPort: GroupJoinTicketPort,
) {
    /** @param reattachableIds 이미 그 저장에 붙어 있던 사진 — 이어쓰기에서 다시 붙일 수 있다 (M2). */
    fun validate(
        userId: Long,
        photoAssetIds: List<Long>,
        companionTagIds: List<String>,
        positivePointTagIds: List<String>,
        rating: Int?,
        content: String?,
        reattachableIds: Set<Long> = emptySet(),
    ) {
        if (photoAssetIds.size > SaveRules.PHOTO_MAX_COUNT) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "사진은 최대 ${SaveRules.PHOTO_MAX_COUNT}장입니다.")
        }
        // save_photo.media_asset_id UNIQUE가 거부하는 자리를 미리 막는다
        if (photoAssetIds.distinct().size != photoAssetIds.size) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "photoAssetIds에 같은 사진이 두 번 들어 있습니다.")
        }
        attachMediaUseCase.verifyAttachable(
            ownerId = userId,
            assetIds = photoAssetIds,
            reattachableIds = reattachableIds,
        )

        verifyTags(companionTagIds, companion = true)
        verifyTags(positivePointTagIds, companion = false)

        rating?.let {
            if (it !in SaveRules.RATING_MIN..SaveRules.RATING_MAX) {
                throw TmtException(ErrorCode.VALIDATION_FAILED, "rating은 1~5 정수입니다.")
            }
        }
        content?.let {
            // 코드포인트로 센다 — String.length는 UTF-16 코드 유닛이라 이모지 한 자가 2로 잡힌다.
            // 화면 카운터가 세는 글자 수(명세 500자)와 맞추지 않으면 480자에서 거부되는 일이 생긴다
            if (it.codePointCount(0, it.length) > SaveRules.CONTENT_MAX_LENGTH) {
                throw TmtException(ErrorCode.REVIEW_CONTENT_TOO_LONG)
            }
        }
    }

    /** 보유 999장 미만일 때만 발급한다 (T6). 상한이면 리뷰는 성립하고 티켓만 안 나간다. */
    fun tryGrantTicket(
        userId: Long,
        reviewId: Long,
    ): Int {
        if (groupJoinTicketPort.countAvailable(userId) >= SaveRules.TICKET_MAX_AVAILABLE) return 0
        groupJoinTicketPort.grantForReview(userId, reviewId)
        return 1
    }

    fun availableTicketCount(userId: Long): Int = groupJoinTicketPort.countAvailable(userId)

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
