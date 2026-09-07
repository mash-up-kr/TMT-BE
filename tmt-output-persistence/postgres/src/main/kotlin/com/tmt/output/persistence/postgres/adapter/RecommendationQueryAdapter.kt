package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.CandidatePlaceRow
import com.tmt.application.port.output.persistence.RecommendationQueryPort
import com.tmt.application.port.output.persistence.RecommendedPlaceRow
import com.tmt.application.port.output.persistence.ReviewedPlaceRow
import com.tmt.application.port.output.persistence.SeedPlaceRow
import com.tmt.output.persistence.postgres.repository.RecommendationQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class RecommendationQueryAdapter(
    private val recommendationQueryRepository: RecommendationQueryRepository,
) : RecommendationQueryPort {
    override fun findReviewedPlaceRows(
        userId: Long,
        afterLatestReviewedAt: Instant?,
        afterPlaceId: Long?,
        limitPlusOne: Int,
    ): List<ReviewedPlaceRow> =
        recommendationQueryRepository
            .findReviewedPlaceRows(userId, afterLatestReviewedAt, afterPlaceId, limitPlusOne)
            .map {
                ReviewedPlaceRow(
                    placeId = it.getPlaceId(),
                    name = it.getName(),
                    categoryId = it.getCategoryId(),
                    thumbnailS3Key = it.getThumbnailS3Key(),
                    latestReviewedAt = it.getLatestReviewedAt(),
                )
            }

    override fun findReviewedPlaceIdsAmong(
        userId: Long,
        placeIds: List<Long>,
    ): List<Long> {
        // 빈 목록은 SQL의 IN ()이 문법 오류라 쿼리에 닿기 전에 끊는다
        if (placeIds.isEmpty()) return emptyList()
        return recommendationQueryRepository.findReviewedPlaceIdsAmong(userId, placeIds)
    }

    override fun findSeedPlaces(
        userId: Long,
        placeIds: List<Long>,
    ): List<SeedPlaceRow> {
        if (placeIds.isEmpty()) return emptyList()
        return recommendationQueryRepository.findSeedPlaces(userId, placeIds).map {
            SeedPlaceRow(
                placeId = it.getPlaceId(),
                name = it.getName(),
                categoryId = it.getCategoryId(),
                rating = it.getRating(),
                content = it.getContent(),
            )
        }
    }

    override fun findCandidatePlaces(
        userId: Long,
        seedPlaceIds: List<Long>,
        limit: Int,
    ): List<CandidatePlaceRow> {
        if (seedPlaceIds.isEmpty()) return emptyList()
        return recommendationQueryRepository.findCandidatePlaces(userId, seedPlaceIds, limit).map {
            CandidatePlaceRow(
                placeId = it.getPlaceId(),
                name = it.getName(),
                categoryId = it.getCategoryId(),
                regionName = it.getRegionName(),
                reviewCount = it.getReviewCount(),
                averageRating = it.getAverageRating(),
            )
        }
    }

    override fun findRecommendedPlace(placeId: Long): RecommendedPlaceRow? =
        recommendationQueryRepository.findRecommendedPlace(placeId)?.let {
            RecommendedPlaceRow(
                placeId = it.getPlaceId(),
                name = it.getName(),
                roadAddress = it.getRoadAddress(),
                categoryId = it.getCategoryId(),
                thumbnailS3Key = it.getThumbnailS3Key(),
                summaryReviewId = it.getSummaryReviewId(),
                summaryPros = it.getSummaryPros(),
                summaryCons = it.getSummaryCons(),
            )
        }
}
