package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewDeletionRow
import com.tmt.application.port.output.persistence.ReviewDetailRow
import com.tmt.application.port.output.persistence.ReviewQueryPort
import com.tmt.output.persistence.postgres.repository.ReviewDetailRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ReviewQueryAdapter(
    private val reviewDetailRepository: ReviewDetailRepository,
) : ReviewQueryPort {
    override fun findReviewDetail(reviewId: Long): ReviewDetailRow? =
        reviewDetailRepository.findReviewDetail(reviewId)?.let {
            ReviewDetailRow(
                reviewId = it.getReviewId(),
                saveId = it.getSaveId(),
                createdAt = it.getCreatedAt(),
                rating = it.getRating(),
                content = it.getContent(),
                authorId = it.getAuthorId(),
                authorNickname = it.getAuthorNickname(),
                authorProfileImageUrl = it.getAuthorProfileImageUrl(),
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                placeRoadAddress = it.getPlaceRoadAddress(),
                placeCategoryId = it.getPlaceCategoryId(),
            )
        }

    override fun findReviewForDeletion(reviewId: Long): ReviewDeletionRow? =
        reviewDetailRepository.findReviewForDeletion(reviewId)?.let {
            ReviewDeletionRow(
                reviewId = it.getReviewId(),
                saveId = it.getSaveId(),
                userId = it.getUserId(),
                placeId = it.getPlaceId(),
                rating = it.getRating(),
            )
        }
}
