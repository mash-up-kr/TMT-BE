package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.PlaceDetailRow
import com.tmt.application.port.output.persistence.PlaceFavoritePort
import com.tmt.application.port.output.persistence.PlacePhotoRow
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.application.port.output.persistence.PlaceReviewRows
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.output.persistence.postgres.repository.PlaceQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceQueryAdapter(
    private val placeQueryRepository: PlaceQueryRepository,
) : PlaceQueryPort,
    PlaceFavoritePort {
    @Transactional(readOnly = true)
    override fun findPlaceDetail(
        placeId: Long,
        viewerId: Long?,
    ): PlaceDetailRow? =
        placeQueryRepository.findDetail(placeId, viewerId)?.let {
            PlaceDetailRow(
                placeId = it.getPlaceId(),
                name = it.getName(),
                categoryId = it.getCategoryId(),
                ratingSum = it.getRatingSum(),
                reviewCount = it.getReviewCount(),
                roadAddress = it.getRoadAddress(),
                latitude = it.getLatitude(),
                longitude = it.getLongitude(),
                phoneNumber = it.getPhoneNumber(),
                favorite = it.getFavorite(),
            )
        }

    @Transactional(readOnly = true)
    override fun existsPlace(placeId: Long): Boolean = placeQueryRepository.existsById(placeId)

    @Transactional(readOnly = true)
    override fun findRecentPlacePhotos(
        placeId: Long,
        limit: Int,
    ): List<PlacePhotoRow> =
        placeQueryRepository.findRecentPhotos(placeId, limit).map {
            PlacePhotoRow(s3Key = it.getS3Key(), reviewId = it.getReviewId())
        }

    @Transactional(readOnly = true)
    override fun findPlaceReviewRows(
        placeId: Long,
        afterCreatedAt: java.time.Instant?,
        afterReviewId: Long?,
        limit: Int,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
    ): PlaceReviewRows {
        val rows =
            placeQueryRepository.findPlaceReviewRows(
                placeId = placeId,
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

    @Transactional
    override fun add(
        userId: Long,
        placeId: Long,
    ) {
        placeQueryRepository.addFavorite(userId, placeId)
    }

    @Transactional
    override fun remove(
        userId: Long,
        placeId: Long,
    ) {
        placeQueryRepository.removeFavorite(userId, placeId)
    }
}
