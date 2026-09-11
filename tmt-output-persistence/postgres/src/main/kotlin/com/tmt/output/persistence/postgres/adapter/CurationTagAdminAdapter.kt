package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.CurationTagAdminPort
import com.tmt.application.port.output.persistence.CurationTagAdminRow
import com.tmt.application.port.output.persistence.CurationTagPlaceRow
import com.tmt.output.persistence.postgres.entity.CurationTagPlaceEntity
import com.tmt.output.persistence.postgres.entity.CurationTagPlaceId
import com.tmt.output.persistence.postgres.repository.CurationTagAdminRepository
import com.tmt.output.persistence.postgres.repository.CurationTagPlaceRepository
import org.springframework.stereotype.Component

/**
 * 쓰기 메서드에 `@Transactional`을 달지 않는다 — 호출부(`CurationTagAdminService`)가 연
 * 트랜잭션에 참여해야 교체의 삭제·삽입이 한 단위로 커밋된다.
 */
@Component
class CurationTagAdminAdapter(
    private val curationTagAdminRepository: CurationTagAdminRepository,
    private val curationTagPlaceRepository: CurationTagPlaceRepository,
) : CurationTagAdminPort {
    override fun findAllTags(): List<CurationTagAdminRow> = curationTagAdminRepository.findTagRows(null).map(::toRow)

    override fun findTag(curationTagId: String): CurationTagAdminRow? =
        curationTagAdminRepository.findTagRows(curationTagId).firstOrNull()?.let(::toRow)

    override fun createTag(
        curationTagId: String,
        label: String,
        displayOrder: Int,
    ): Boolean = curationTagAdminRepository.insertTagIfAbsent(curationTagId, label, displayOrder) == 1

    override fun updateTag(
        curationTagId: String,
        label: String?,
        displayOrder: Int?,
        active: Boolean?,
    ): Boolean = curationTagAdminRepository.updateTag(curationTagId, label, displayOrder, active) == 1

    override fun findTagPlaces(curationTagId: String): List<CurationTagPlaceRow> =
        curationTagAdminRepository.findTagPlaceRows(curationTagId).map {
            CurationTagPlaceRow(
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                roadAddress = it.getRoadAddress(),
                pinOrder = it.getPinOrder(),
            )
        }

    override fun replaceTagPlaces(
        curationTagId: String,
        placeIds: List<Long>,
    ) {
        curationTagPlaceRepository.deleteAllByCurationTagId(curationTagId)
        if (placeIds.isEmpty()) return
        // 보낸 순서가 pin_order 1..n이다 — V10 시드의 row_number()와 같은 기준
        curationTagPlaceRepository.saveAll(
            placeIds.mapIndexed { index, placeId ->
                CurationTagPlaceEntity(
                    id = CurationTagPlaceId(curationTagId = curationTagId, placeId = placeId),
                    pinOrder = (index + 1).toShort(),
                )
            },
        )
    }

    override fun findMissingPlaceIds(placeIds: List<Long>): List<Long> {
        if (placeIds.isEmpty()) return emptyList()
        val existing = curationTagAdminRepository.findExistingPlaceIds(placeIds).toSet()
        return placeIds.filterNot { it in existing }.distinct()
    }

    private fun toRow(view: CurationTagAdminRepository.CurationTagAdminRowView) =
        CurationTagAdminRow(
            curationTagId = view.getCurationTagId(),
            label = view.getLabel(),
            displayOrder = view.getDisplayOrder(),
            active = view.getActive(),
            placeCount = view.getPlaceCount(),
        )
}
