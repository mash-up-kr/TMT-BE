package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.NewReviewSummary
import com.tmt.application.port.output.persistence.PendingReviewSummary
import com.tmt.application.port.output.persistence.ReviewAiSummaryPort
import com.tmt.output.persistence.postgres.entity.ReviewAiSummaryEntity
import com.tmt.output.persistence.postgres.repository.ReviewAiSummaryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReviewAiSummaryAdapter(
    private val reviewAiSummaryRepository: ReviewAiSummaryRepository,
) : ReviewAiSummaryPort {
    @Transactional(readOnly = true)
    override fun findPendingReviews(limit: Int): List<PendingReviewSummary> =
        reviewAiSummaryRepository.findPendingReviews(limit).map {
            PendingReviewSummary(
                reviewId = it.getReviewId(),
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                rating = it.getRating(),
                content = it.getContent(),
            )
        }

    @Transactional
    override fun saveSummaries(summaries: List<NewReviewSummary>) {
        reviewAiSummaryRepository.saveAll(
            summaries.map {
                ReviewAiSummaryEntity(
                    reviewId = it.reviewId,
                    pros = it.pros,
                    cons = it.cons,
                    model = it.model,
                )
            },
        )
    }
}
