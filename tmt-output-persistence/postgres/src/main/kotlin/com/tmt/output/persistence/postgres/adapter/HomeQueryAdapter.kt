package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupCardRow
import com.tmt.application.port.output.persistence.HomeFeedRows
import com.tmt.application.port.output.persistence.HomeQueryPort
import com.tmt.application.port.output.persistence.MyGroupRow
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.output.persistence.postgres.repository.HomeQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class HomeQueryAdapter(
    private val homeQueryRepository: HomeQueryRepository,
) : HomeQueryPort {
    override fun findNickname(userId: Long): String? = homeQueryRepository.findNickname(userId)

    override fun findMyGroups(userId: Long): List<MyGroupRow> =
        homeQueryRepository.findMyGroups(userId).map {
            MyGroupRow(groupId = it.getGroupId(), name = it.getName(), imageS3Key = it.getImageS3Key())
        }

    override fun findRecommendedGroups(
        userId: Long,
        limit: Int,
    ): List<GroupCardRow> =
        homeQueryRepository.findRecommendedGroups(userId, limit).map {
            GroupCardRow(
                groupId = it.getGroupId(),
                name = it.getName(),
                oneLineDescription = it.getOneLineDescription(),
                coverS3Key = it.getCoverS3Key(),
                memberCount = it.getMemberCount(),
                reviewCount = it.getReviewCount(),
                placeCount = it.getPlaceCount(),
                matchedSavedPlaceCount = it.getMatchedSavedPlaceCount(),
            )
        }

    override fun findFeedRowsByDistance(
        userId: Long,
        latitude: Double,
        longitude: Double,
        afterDistanceMeters: Int?,
        afterReviewId: Long?,
        limit: Int,
    ): HomeFeedRows =
        toRows(
            homeQueryRepository.findFeedRowsByDistance(
                userId = userId,
                lat = latitude,
                lng = longitude,
                afterDistance = afterDistanceMeters,
                afterReviewId = afterReviewId,
                limitPlusOne = limit + 1,
            ),
            limit,
        )

    override fun findFeedRowsByRecency(
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
    ): HomeFeedRows =
        toRows(
            homeQueryRepository.findFeedRowsByRecency(
                userId = userId,
                afterCreatedAt = afterCreatedAt,
                afterReviewId = afterReviewId,
                limitPlusOne = limit + 1,
            ),
            limit,
        )

    private fun toRows(
        views: List<HomeQueryRepository.HomeFeedRowView>,
        limit: Int,
    ): HomeFeedRows =
        HomeFeedRows(
            rows =
                views.take(limit).map {
                    ReviewCardRow(
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
                        placeRegionName = it.getPlaceRegionName(),
                        placeCategoryId = it.getPlaceCategoryId(),
                        distanceMeters = it.getDistanceMeters(),
                        favorite = it.getFavorite(),
                    )
                },
            hasNext = views.size > limit,
        )
}
