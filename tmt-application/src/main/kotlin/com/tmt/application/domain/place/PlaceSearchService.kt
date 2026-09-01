package com.tmt.application.domain.place

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.PlaceCardView
import com.tmt.application.port.input.PlaceSearchKey
import com.tmt.application.port.input.PlaceSearchRequest
import com.tmt.application.port.input.PlaceSearchResult
import com.tmt.application.port.input.SearchPlacesUseCase
import com.tmt.application.port.output.persistence.PlaceSearchCriteria
import com.tmt.application.port.output.persistence.PlaceSearchPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class PlaceSearchService(
    private val placeSearchPort: PlaceSearchPort,
    private val reviewCardComposer: ReviewCardComposer,
) : SearchPlacesUseCase {
    override fun search(request: PlaceSearchRequest): PlaceSearchResult {
        val query = request.query?.takeIf { it.isNotBlank() }
        if (query == null && request.curationTagId == null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "query와 curationTagId 중 최소 하나는 있어야 합니다.")
        }
        val latitude = request.latitude
        val longitude = request.longitude
        val hasCoordinates = latitude != null && longitude != null
        if (request.nearbyOnly && !hasCoordinates) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "nearbyOnly=true에는 좌표가 함께 있어야 합니다.")
        }
        if (latitude != null && longitude != null && (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 위경도 범위 안이어야 합니다.")
        }

        val preset = request.curationTagId?.let { CurationPresets.BY_ID[it] }
        // 알 수 없는 칩은 mock과 같게 빈 결과다 — 쿼리를 낼 이유가 없다
        if (request.curationTagId != null && preset == null) {
            return PlaceSearchResult(emptyList(), hasNext = false, lastKey = null)
        }

        val page =
            placeSearchPort.search(
                PlaceSearchCriteria(
                    query = query,
                    queryCategoryIds =
                        query
                            ?.let { q ->
                                FoodCategories.LABEL_BY_ID
                                    .filterValues { it.contains(q) }
                                    .keys
                                    .toList()
                            }.orEmpty(),
                    categoryId = preset?.categoryId,
                    regionPrefix = preset?.regionPrefix,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = if (request.nearbyOnly) NEARBY_RADIUS_METERS else null,
                    sortByDistance = hasCoordinates,
                    afterSortValue = request.after?.sortValue,
                    afterPlaceId = request.after?.placeId,
                    limit = request.limit,
                    viewerId = request.viewerId,
                ),
            )

        val thumbnails = placeSearchPort.findLatestPhotoKeys(page.rows.map { it.placeId })
        val items =
            page.rows.map { row ->
                PlaceCardView(
                    placeId = row.placeId,
                    name = row.name,
                    roadAddress = row.roadAddress,
                    regionName = row.regionName,
                    categoryName = FoodCategories.labelOf(row.categoryId),
                    // 평균 = rating_sum / review_count (P9), 소수 첫째 자리. 리뷰 0건이면 null
                    averageRating =
                        row.reviewCount.takeIf { it > 0 }?.let {
                            (row.ratingSum * 10.0 / it).roundToInt() / 10.0
                        },
                    reviewCount = row.reviewCount,
                    thumbnailUrl = thumbnails[row.placeId]?.let(reviewCardComposer::mediaUrl),
                    distanceMeters = row.distanceMeters,
                    isFavorite = row.favorite,
                )
            }
        return PlaceSearchResult(
            items = items,
            hasNext = page.hasNext,
            lastKey = page.rows.lastOrNull()?.let { PlaceSearchKey(it.sortValue, it.placeId) },
        )
    }

    companion object {
        /** nearbyOnly=true의 반경 — 근처 피드와 같은 서버 고정값이다 (E1) */
        const val NEARBY_RADIUS_METERS = 1_000
    }
}
