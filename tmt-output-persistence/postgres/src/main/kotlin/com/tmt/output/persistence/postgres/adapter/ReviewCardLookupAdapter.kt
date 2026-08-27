package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import com.tmt.output.persistence.postgres.repository.NearbyQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ReviewCardLookupAdapter(
    private val nearbyQueryRepository: NearbyQueryRepository,
) : ReviewCardLookupPort {
    override fun findPhotoRows(saveIds: Collection<Long>): List<PhotoRow> =
        nearbyQueryRepository.findPhotoRows(saveIds).map {
            PhotoRow(it.getSaveId(), it.getSavePhotoId(), it.getS3Key(), it.getPhotoOrder())
        }

    override fun findTagRows(saveIds: Collection<Long>): List<TagRow> =
        nearbyQueryRepository.findTagRows(saveIds).map {
            TagRow(it.getSaveId(), it.getTagId(), it.getLabel())
        }

    override fun findSummaryRows(reviewIds: Collection<Long>): List<SummaryRow> =
        nearbyQueryRepository.findSummaryRows(reviewIds).map {
            SummaryRow(it.getReviewId(), it.getPros(), it.getCons())
        }
}
