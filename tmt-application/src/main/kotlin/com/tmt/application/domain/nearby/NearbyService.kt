package com.tmt.application.domain.nearby

import com.tmt.application.domain.place.CurationPresets
import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.GetNearbyPlacesUseCase
import com.tmt.application.port.input.GetNearbyReviewsUseCase
import com.tmt.application.port.input.NearbyPlacesRequest
import com.tmt.application.port.input.NearbyPlacesResult
import com.tmt.application.port.input.NearbyReviewsRequest
import com.tmt.application.port.input.NearbyReviewsResult
import com.tmt.application.port.input.ReviewCardView
import com.tmt.application.port.output.persistence.NearbyQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class NearbyService(
    private val nearbyQueryPort: NearbyQueryPort,
    @param:Value("\${tmt.media.base-url:}") private val mediaBaseUrl: String,
) : GetNearbyReviewsUseCase,
    GetNearbyPlacesUseCase {
    override fun get(request: NearbyReviewsRequest): NearbyReviewsResult {
        requireCoordinates(request.latitude, request.longitude)

        val page =
            nearbyQueryPort.findReviewRowsWithin(
                latitude = request.latitude,
                longitude = request.longitude,
                radiusMeters = NEARBY_RADIUS_METERS,
                afterDistanceMeters = request.after?.distanceMeters,
                afterReviewId = request.after?.reviewId,
                limit = request.limit,
                viewerId = request.viewerId,
            )
        if (page.rows.isEmpty()) return NearbyReviewsResult(emptyList(), hasNext = false)

        val saveIds = page.rows.map { it.saveId }
        val photosBySave = nearbyQueryPort.findPhotoRows(saveIds).groupBy { it.saveId }
        val tagsBySave = nearbyQueryPort.findTagRows(saveIds).groupBy { it.saveId }
        val summaryByReview = nearbyQueryPort.findSummaryRows(page.rows.map { it.reviewId }).associateBy { it.reviewId }

        val items =
            page.rows.map { row ->
                ReviewCardView(
                    reviewId = row.reviewId,
                    authorId = row.authorId,
                    authorNickname = row.authorNickname,
                    authorProfileImageUrl = row.authorProfileImageUrl,
                    rating = row.rating,
                    distanceMeters = row.distanceMeters,
                    photos =
                        photosBySave[row.saveId].orEmpty().sortedBy { it.photoOrder }.map { photo ->
                            ReviewCardView.Photo(
                                photoId = photo.savePhotoId,
                                // 공개 읽기 버킷 (TMT-201) — base-url + s3_key가 곧 조회 URL이다
                                url = "${mediaBaseUrl.trimEnd('/')}/${photo.s3Key}",
                                order = photo.photoOrder,
                            )
                        },
                    aiSummary =
                        summaryByReview[row.reviewId]?.let { ReviewCardView.AiSummary(it.pros, it.cons) },
                    content = row.content,
                    tags = tagsBySave[row.saveId].orEmpty().map { ReviewCardView.Tag(it.tagId, it.label) },
                    placeId = row.placeId,
                    placeName = row.placeName,
                    placeRegionName = row.placeRegionName,
                    placeFavorite = row.favorite,
                    createdAt = row.createdAt,
                )
            }
        return NearbyReviewsResult(items, hasNext = page.hasNext)
    }

    override fun get(request: NearbyPlacesRequest): NearbyPlacesResult {
        if (request.south >= request.north || request.west >= request.east) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "viewport 경계가 뒤집혀 있습니다.")
        }
        val preset = request.curationTagId?.let { CurationPresets.BY_ID[it] }
        // 알 수 없는 칩은 mock과 같게 빈 결과다 — 쿼리를 낼 이유가 없다
        if (request.curationTagId != null && preset == null) return NearbyPlacesResult(emptyList(), truncated = false)

        val rows =
            nearbyQueryPort.findPins(
                north = request.north,
                south = request.south,
                east = request.east,
                west = request.west,
                centerLatitude = request.centerLatitude,
                centerLongitude = request.centerLongitude,
                query = request.query?.takeIf { it.isNotBlank() },
                queryCategoryIds =
                    request.query
                        ?.takeIf { it.isNotBlank() }
                        ?.let { q ->
                            FoodCategories.LABEL_BY_ID
                                .filterValues { it.contains(q) }
                                .keys
                                .toList()
                        }.orEmpty(),
                categoryId = preset?.categoryId,
                regionPrefix = preset?.regionPrefix,
                limit = MAX_PINS,
            )

        val truncated = rows.size > MAX_PINS
        return NearbyPlacesResult(
            pins =
                rows.take(MAX_PINS).map {
                    NearbyPlacesResult.Pin(it.placeId, it.name, it.latitude, it.longitude, it.reviewCount)
                },
            truncated = truncated,
        )
    }

    private fun requireCoordinates(
        latitude: Double,
        longitude: Double,
    ) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 위경도 범위 안이어야 합니다.")
        }
    }

    companion object {
        /** 반경은 서버가 고정한다 (E1) — 클라이언트가 넓히지 못한다 */
        const val NEARBY_RADIUS_METERS = 1_000

        /** 지도 핀 상한 (E8) — 초과 시 중심 가까운 순 30개 + truncated */
        const val MAX_PINS = 30
    }
}
