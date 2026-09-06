package com.tmt.application.domain.group

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.CreateGroupUseCase
import com.tmt.application.port.input.GroupCommand
import com.tmt.application.port.input.GroupDetailView
import com.tmt.application.port.input.UpdateGroupUseCase
import com.tmt.application.port.output.persistence.GroupCommandPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹 생성·편집 (D_02, TMT-221). */
@Service
class GroupCommandService(
    private val groupCommandPort: GroupCommandPort,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val groupDetailComposer: GroupDetailComposer,
) : CreateGroupUseCase,
    UpdateGroupUseCase {
    @Transactional
    override fun create(command: GroupCommand): GroupDetailView {
        validate(command)

        val groupId =
            groupCommandPort.create(
                ownerId = command.requesterId,
                name = command.name,
                oneLineDescription = command.oneLineDescription,
                description = command.description,
                foodCategoryId = command.foodCategoryId,
                regionTagIds = command.regionTagIds,
                imageAssetId = command.imageAssetId,
            )
        // 대표 이미지도 리뷰 사진과 같은 업로드 경로다(M7) — ATTACHED로 전이해야 TTL 정리(M4)가 지우지 않는다
        command.imageAssetId?.let { attachMediaUseCase.attach(listOf(it)) }

        return requireNotNull(groupDetailComposer.compose(groupId, command.requesterId))
    }

    @Transactional
    override fun update(
        groupId: Long,
        command: GroupCommand,
    ): GroupDetailView {
        val target = groupCommandPort.findEditTarget(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        if (target.ownerId != command.requesterId) {
            throw TmtException(ErrorCode.GROUP_OWNER_REQUIRED)
        }
        validate(command, currentImageAssetId = target.imageAssetId)

        groupCommandPort.update(
            groupId = groupId,
            name = command.name,
            oneLineDescription = command.oneLineDescription,
            description = command.description,
            foodCategoryId = command.foodCategoryId,
            regionTagIds = command.regionTagIds,
            imageAssetId = command.imageAssetId,
        )
        // 이미지를 교체하면 이전 asset은 STAGED로 되돌려 TTL 정리 대상이 되게 한다
        if (command.imageAssetId != target.imageAssetId) {
            target.imageAssetId?.let { attachMediaUseCase.detach(listOf(it)) }
            command.imageAssetId?.let { attachMediaUseCase.attach(listOf(it)) }
        }

        return requireNotNull(groupDetailComposer.compose(groupId, command.requesterId))
    }

    private fun validate(
        command: GroupCommand,
        currentImageAssetId: Long? = null,
    ) {
        if (command.name.isBlank() || command.oneLineDescription.isBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name과 oneLineDescription은 필수입니다.")
        }
        // 상한은 컬럼 폭(V1: name 50·one_line 100)이다. 여기서 안 막으면 INSERT가
        // DataIntegrityViolationException으로 실패해 "이미 있는 그룹명"으로 잘못 나간다 (PR #80 리뷰).
        // 코드 포인트 기준 — DB char_length와 같은 단위라 이모지가 든 이름에서 어긋나지 않는다
        if (command.name.codePointCount() > NAME_MAX_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name은 최대 ${NAME_MAX_LENGTH}자입니다.")
        }
        if (command.oneLineDescription.codePointCount() > ONE_LINE_MAX_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "oneLineDescription은 최대 ${ONE_LINE_MAX_LENGTH}자입니다.")
        }
        if (command.regionTagIds.isEmpty()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "regionTagIds는 최소 1개입니다.")
        }
        if ((command.description?.codePointCount() ?: 0) > DESCRIPTION_MAX_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "description은 최대 ${DESCRIPTION_MAX_LENGTH}자입니다.")
        }
        if (command.foodCategoryId !in GroupTagCatalog.FOOD_CATEGORY_IDS) {
            throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, command.foodCategoryId)
        }
        command.regionTagIds.forEach {
            if (it !in GroupTagCatalog.REGION_TAG_IDS) throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
        }
        command.imageAssetId?.let { assetId ->
            attachMediaUseCase.verifyAttachable(
                ownerId = command.requesterId,
                assetIds = listOf(assetId),
                // 이미지를 그대로 둔 편집이면 이미 ATTACHED라 재부착을 허용해야 한다
                reattachableIds = setOfNotNull(currentImageAssetId),
            )
        }
    }

    companion object {
        private const val NAME_MAX_LENGTH = 50
        private const val ONE_LINE_MAX_LENGTH = 100
        private const val DESCRIPTION_MAX_LENGTH = 200

        private fun String.codePointCount(): Int = codePointCount(0, length)
    }
}
