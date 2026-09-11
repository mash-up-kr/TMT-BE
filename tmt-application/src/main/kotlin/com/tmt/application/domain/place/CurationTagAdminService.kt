package com.tmt.application.domain.place

import com.tmt.application.port.input.CreateCurationTagUseCase
import com.tmt.application.port.input.CurationTagAdminView
import com.tmt.application.port.input.CurationTagCreateCommand
import com.tmt.application.port.input.CurationTagPlaceView
import com.tmt.application.port.input.CurationTagUpdateCommand
import com.tmt.application.port.input.GetCurationTagPlacesUseCase
import com.tmt.application.port.input.ListCurationTagsForAdminUseCase
import com.tmt.application.port.input.ReplaceCurationTagPlacesUseCase
import com.tmt.application.port.input.UpdateCurationTagUseCase
import com.tmt.application.port.output.persistence.CurationTagAdminPort
import com.tmt.application.port.output.persistence.CurationTagAdminRow
import com.tmt.application.port.output.persistence.CurationTagPlaceRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 큐레이션 칩 운영 (E12, TMT-421). 어드민 판정은 입력 어댑터가 경로로 막으므로
 * 여기서는 **값 검증과 무결성만** 본다.
 *
 * 검증 상한은 명세에 없어 여기서 정한 값이다 — 계약 변경 이력 §4에 올려 뒀다.
 * 상한이 없으면 운영 실수 한 번이 컬럼 폭 초과(기동 중 INSERT 실패)나
 * 화면이 훑을 수 없는 크기의 칩을 만든다.
 */
@Service
class CurationTagAdminService(
    private val curationTagAdminPort: CurationTagAdminPort,
) : ListCurationTagsForAdminUseCase,
    CreateCurationTagUseCase,
    UpdateCurationTagUseCase,
    GetCurationTagPlacesUseCase,
    ReplaceCurationTagPlacesUseCase {
    @Transactional(readOnly = true)
    override fun list(): List<CurationTagAdminView> = curationTagAdminPort.findAllTags().map(::toView)

    @Transactional
    override fun create(command: CurationTagCreateCommand): CurationTagAdminView {
        val id = command.curationTagId
        if (!ID_PATTERN.matches(id)) {
            throw TmtException(
                ErrorCode.VALIDATION_FAILED,
                "curationTagId는 'curation_' 뒤에 영소문자·숫자·밑줄 1~20자여야 합니다.",
            )
        }
        val label = validLabel(command.label)
        validateDisplayOrder(command.displayOrder)

        // 이미 있으면 덮어쓰지 않는다 — 운영 화면의 생성이 기존 칩을 조용히 바꾸면 안 된다
        if (!curationTagAdminPort.createTag(id, label, command.displayOrder)) {
            throw TmtException(ErrorCode.CURATION_TAG_DUPLICATED, id)
        }
        return toView(requireNotNull(curationTagAdminPort.findTag(id)) { "방금 만든 칩이 없다" })
    }

    @Transactional
    override fun update(
        curationTagId: String,
        command: CurationTagUpdateCommand,
    ): CurationTagAdminView {
        val label = command.label?.let(::validLabel)
        command.displayOrder?.let(::validateDisplayOrder)
        if (label == null && command.displayOrder == null && command.active == null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "바꿀 값이 하나도 없습니다.")
        }

        if (!curationTagAdminPort.updateTag(curationTagId, label, command.displayOrder, command.active)) {
            throw TmtException(ErrorCode.CURATION_TAG_NOT_FOUND, curationTagId)
        }
        return toView(requireNotNull(curationTagAdminPort.findTag(curationTagId)) { "방금 고친 칩이 없다" })
    }

    @Transactional(readOnly = true)
    override fun get(curationTagId: String): List<CurationTagPlaceView> {
        requireTagExists(curationTagId)
        return curationTagAdminPort.findTagPlaces(curationTagId).map(::toView)
    }

    @Transactional
    override fun replace(
        curationTagId: String,
        placeIds: List<Long>,
    ): List<CurationTagPlaceView> {
        requireTagExists(curationTagId)
        if (placeIds.size > MAX_PLACES) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "칩 하나에 매장은 최대 ${MAX_PLACES}곳입니다.")
        }
        // 중복은 조용히 접지 않는다 — pin_order가 밀려 운영이 보낸 순서와 결과가 달라진다
        if (placeIds.toSet().size != placeIds.size) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "placeIds에 중복이 있습니다.")
        }
        // 존재 여부를 먼저 본다 — FK 위반으로 터지면 어느 매장이 문제인지 응답에 남지 않는다
        val missing = curationTagAdminPort.findMissingPlaceIds(placeIds)
        if (missing.isNotEmpty()) {
            throw TmtException(ErrorCode.PLACE_NOT_FOUND, missing.joinToString(",") { "place_$it" })
        }

        curationTagAdminPort.replaceTagPlaces(curationTagId, placeIds)
        return curationTagAdminPort.findTagPlaces(curationTagId).map(::toView)
    }

    private fun requireTagExists(curationTagId: String) {
        // 비활성 칩도 편집 대상이다 — 내린 칩을 다시 올리려면 읽고 고칠 수 있어야 한다
        curationTagAdminPort.findTag(curationTagId)
            ?: throw TmtException(ErrorCode.CURATION_TAG_NOT_FOUND, curationTagId)
    }

    private fun validLabel(raw: String): String {
        val label = raw.trim()
        if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "label은 1~${MAX_LABEL_LENGTH}자여야 합니다.")
        }
        return label
    }

    private fun validateDisplayOrder(displayOrder: Int) {
        if (displayOrder !in 0..MAX_DISPLAY_ORDER) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "displayOrder는 0~${MAX_DISPLAY_ORDER}여야 합니다.")
        }
    }

    private fun toView(row: CurationTagAdminRow) =
        CurationTagAdminView(
            curationTagId = row.curationTagId,
            label = row.label,
            displayOrder = row.displayOrder,
            active = row.active,
            placeCount = row.placeCount,
        )

    private fun toView(row: CurationTagPlaceRow) =
        CurationTagPlaceView(
            placeId = row.placeId,
            placeName = row.placeName,
            roadAddress = row.roadAddress,
            pinOrder = row.pinOrder,
        )

    companion object {
        /**
         * `curation_` 접두를 강제한다 — 이 값이 그대로 검색·핀의 `curationTagId` 파라미터이고
         * FE·mock에 박히므로, 접두가 섞이면 칩인지 구분할 수 없다. 전체 길이는 컬럼 폭 30자 안이다.
         */
        private val ID_PATTERN = Regex("^curation_[a-z0-9_]{1,20}$")

        /** `curation_tag.label` 컬럼 폭과 같다 — 넘으면 INSERT가 실행 시점에 터진다 */
        private const val MAX_LABEL_LENGTH = 30

        /** SMALLINT 안이면 되지만 화면 칩이 수백 개가 될 일은 없다 */
        private const val MAX_DISPLAY_ORDER = 999

        /**
         * 칩 하나에 100곳. 시드가 30곳이라 여유가 있고, 상한이 없으면 운영이 조건 검색 결과를
         * 그대로 붙여 수천 건을 넣을 수 있다 — 그러면 칩이 "운영이 고른 목록"이 아니게 된다.
         */
        const val MAX_PLACES = 100
    }
}
