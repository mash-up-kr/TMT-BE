package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.PlaceSearchCriteria
import com.tmt.application.port.output.persistence.PlaceSearchPort
import com.tmt.application.port.output.persistence.PlaceSearchRow
import com.tmt.application.port.output.persistence.PlaceSearchRows
import com.tmt.output.persistence.postgres.repository.PlaceSearchRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class PlaceSearchAdapter(
    private val placeSearchRepository: PlaceSearchRepository,
) : PlaceSearchPort {
    override fun search(criteria: PlaceSearchCriteria): PlaceSearchRows {
        // 빈 목록이면 어떤 category_id와도 일치하지 않는 CSV가 된다 ('' 단일 원소)
        val queryCategoryCsv = criteria.queryCategoryIds.joinToString(",")
        val rows =
            if (criteria.sortByDistance) {
                placeSearchRepository.searchByDistance(
                    lat = requireNotNull(criteria.latitude) { "거리 정렬은 좌표가 있을 때만 고른다" },
                    lng = requireNotNull(criteria.longitude) { "거리 정렬은 좌표가 있을 때만 고른다" },
                    radius = criteria.radiusMeters,
                    query = criteria.query,
                    queryCategoryCsv = queryCategoryCsv,
                    categoryId = criteria.categoryId,
                    regionPrefix = criteria.regionPrefix,
                    afterSortValue = criteria.afterSortValue,
                    afterPlaceId = criteria.afterPlaceId,
                    viewerId = criteria.viewerId,
                    limitPlusOne = criteria.limit + 1,
                )
            } else {
                placeSearchRepository.searchByRelevance(
                    query = criteria.query,
                    queryCategoryCsv = queryCategoryCsv,
                    categoryId = criteria.categoryId,
                    regionPrefix = criteria.regionPrefix,
                    afterSortValue = criteria.afterSortValue,
                    afterPlaceId = criteria.afterPlaceId,
                    viewerId = criteria.viewerId,
                    limitPlusOne = criteria.limit + 1,
                )
            }

        return PlaceSearchRows(
            rows =
                rows.take(criteria.limit).map {
                    PlaceSearchRow(
                        placeId = it.getPlaceId(),
                        name = it.getName(),
                        roadAddress = it.getRoadAddress(),
                        regionName = it.getRegionName(),
                        categoryId = it.getCategoryId(),
                        ratingSum = it.getRatingSum(),
                        reviewCount = it.getReviewCount(),
                        distanceMeters = it.getDistanceMeters(),
                        favorite = it.getFavorite(),
                        sortValue = it.getSortValue(),
                    )
                },
            hasNext = rows.size > criteria.limit,
        )
    }

    override fun findLatestPhotoKeys(placeIds: List<Long>): Map<Long, String> {
        if (placeIds.isEmpty()) return emptyMap()
        return placeSearchRepository
            .findLatestPhotoRows(placeIds)
            .associate { it.getPlaceId() to it.getS3Key() }
    }
}
