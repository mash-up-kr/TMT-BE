package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.CurationTagPort
import com.tmt.application.port.output.persistence.CurationTagRow
import com.tmt.output.persistence.postgres.repository.CurationTagRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class CurationTagAdapter(
    private val curationTagRepository: CurationTagRepository,
) : CurationTagPort {
    override fun findActiveTags(): List<CurationTagRow> =
        curationTagRepository
            .findAllByActiveIsTrueOrderByDisplayOrderAsc()
            .map { CurationTagRow(curationTagId = it.id, label = it.label) }

    override fun existsActiveTag(curationTagId: String): Boolean =
        curationTagRepository.existsByIdAndActiveIsTrue(curationTagId)
}
