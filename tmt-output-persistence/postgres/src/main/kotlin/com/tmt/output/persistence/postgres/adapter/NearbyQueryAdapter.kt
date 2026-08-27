package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.NearbyQueryPort
import com.tmt.application.port.output.persistence.NearbyReviewRows
import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.PinRow
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import com.tmt.output.persistence.postgres.repository.NearbyQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class NearbyQueryAdapter(
    private val nearbyQueryRepository: NearbyQueryRepository,
) : NearbyQueryPort {
    override fun findReviewRowsWithin(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        afterDistanceMeters: Int?,
        afterReviewId: Long?,
        limit: Int,
        viewerId: Long?,
    ): NearbyReviewRows {
        val rows =
            nearbyQueryRepository.findNearbyReviewRows(
                lat = latitude,
                lng = longitude,
                radius = radiusMeters,
                afterDistance = afterDistanceMeters,
                afterReviewId = afterReviewId,
                viewerId = viewerId,
                limitPlusOne = limit + 1,
            )
        return NearbyReviewRows(
            rows =
                rows.take(limit).map {
                    NearbyReviewRows.ReviewRow(
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
                        distanceMeters = it.getDistanceMeters(),
                        favorite = it.getFavorite(),
                    )
                },
            hasNext = rows.size > limit,
        )
    }

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

    override fun findPins(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        centerLatitude: Double?,
        centerLongitude: Double?,
        query: String?,
        queryCategoryIds: List<String>,
        categoryId: String?,
        regionPrefix: String?,
        limit: Int,
    ): List<PinRow> =
        nearbyQueryRepository
            .findPins(
                north = north,
                south = south,
                east = east,
                west = west,
                centerLat = centerLatitude,
                centerLng = centerLongitude,
                query = query,
                // 빈 목록이면 어떤 category_id와도 일치하지 않는 CSV가 된다 ('' 단일 원소)
                queryCategoryCsv = queryCategoryIds.joinToString(","),
                categoryId = categoryId,
                regionPrefix = regionPrefix,
                limitPlusOne = limit + 1,
            ).map {
                PinRow(it.getPlaceId(), it.getName(), it.getLatitude(), it.getLongitude(), it.getReviewCount())
            }
}
