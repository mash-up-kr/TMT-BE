package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewTagDefinitionRow
import com.tmt.application.port.output.persistence.ReviewTagPort
import com.tmt.application.port.output.persistence.ReviewTagRow
import com.tmt.output.persistence.postgres.entity.ReviewTagType
import com.tmt.output.persistence.postgres.repository.ReviewTagDefinitionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReviewTagAdapter(
    private val reviewTagDefinitionRepository: ReviewTagDefinitionRepository,
) : ReviewTagPort {
    @Transactional(readOnly = true)
    override fun findActiveTags(tagIds: Collection<String>): List<ReviewTagRow> {
        if (tagIds.isEmpty()) return emptyList()
        return reviewTagDefinitionRepository
            .findAllByIdInAndActiveIsTrue(tagIds)
            .map { ReviewTagRow(tagId = it.id, companion = it.tagType == ReviewTagType.COMPANION) }
    }

    @Transactional(readOnly = true)
    override fun findAllActiveDefinitions(): List<ReviewTagDefinitionRow> =
        reviewTagDefinitionRepository
            .findAllByActiveIsTrueOrderByTagTypeAscDisplayOrderAsc()
            .map {
                ReviewTagDefinitionRow(
                    tagId = it.id,
                    label = it.label,
                    companion = it.tagType == ReviewTagType.COMPANION,
                )
            }
}
