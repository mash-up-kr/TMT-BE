package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.application.port.output.persistence.PlaceReviewRows
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.output.persistence.postgres.repository.GroupReviewQueryRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GroupReviewQueryAdapter(
    private val repository: GroupReviewQueryRepository,
) : GroupReviewQueryPort {
    override fun existsGroup(groupId: Long): Boolean = repository.existsGroup(groupId)

    override fun isMember(
        groupId: Long,
        userId: Long,
    ): Boolean = repository.isMember(groupId, userId)

    override fun findSharedReviewRows(
        groupId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
        limit: Int,
    ): PlaceReviewRows {
        val rows =
            repository.findSharedReviewRows(
                groupId = groupId,
                afterCreatedAt = afterCreatedAt,
                afterReviewId = afterReviewId,
                viewerId = viewerId,
                viewerLat = viewerLatitude,
                viewerLng = viewerLongitude,
                limitPlusOne = limit + 1,
            )
        return PlaceReviewRows(
            rows =
                rows.take(limit).map {
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
            hasNext = rows.size > limit,
        )
    }
}
