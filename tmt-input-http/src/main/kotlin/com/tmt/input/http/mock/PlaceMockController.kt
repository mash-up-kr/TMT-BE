package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PlaceCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.math.roundToInt

@Tag(name = "매장 (mock)", description = "명세 v2 — B §2-2 · F §2")
@RestController
class PlaceMockController(
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockFavoriteStore: MockFavoriteStore,
) {
    @Operation(summary = "매장 검색", description = "가게명·주소·음식 카테고리 태그로 찾는다. 결과 0건은 오류가 아니다 — items: []")
    @GetMapping("/v1/places/search")
    fun searchPlaces(
        @UserId userId: Long?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) curationTagId: String?,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) nearbyOnly: Boolean?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<PlaceCardResponse> {
        if (query.isNullOrBlank() && curationTagId == null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "query와 curationTagId 중 최소 하나는 있어야 합니다.")
        }

        var results = mockPlaceStore.findAll()
        query?.takeIf { it.isNotBlank() }?.let { q -> results = results.filter { it.matchesQuery(q) } }
        curationTagId?.let { chip ->
            results = results.filter(CurationPresets.matcher(chip))
        }
        if (nearbyOnly == true) {
            if (latitude == null || longitude == null) {
                throw TmtException(ErrorCode.VALIDATION_FAILED, "nearbyOnly=true에는 좌표가 함께 있어야 합니다.")
            }
            results =
                results.filter {
                    MockGeo.distanceMeters(latitude, longitude, it.latitude, it.longitude) <=
                        MockGeo.NEARBY_RADIUS_METERS
                }
        }
        if (latitude != null && longitude != null) {
            results =
                results.sortedWith(
                    compareBy(
                        { MockGeo.distanceMeters(latitude, longitude, it.latitude, it.longitude) },
                        { it.placeId },
                    ),
                )
        }

        return MockCursor.paginate(results, cursor, limit) { toPlaceCard(it, userId, latitude, longitude) }
    }

    private fun toPlaceCard(
        place: MockPlace,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
    ): PlaceCardResponse {
        val reviews = mockSaveStore.findAll().filter { it.placeId == place.placeId && it.reviewId != null }
        val ratings = reviews.mapNotNull { it.rating }
        val latestPhoto = reviews.maxByOrNull { it.updatedAt }?.photoAssetIds?.firstOrNull()
        return PlaceCardResponse(
            placeId = place.placeId,
            name = place.name,
            roadAddress = place.roadAddress,
            regionName = place.regionName,
            categoryName = place.categoryName,
            averageRating = ratings.takeIf { it.isNotEmpty() }?.let { (it.average() * 10).roundToInt() / 10.0 },
            reviewCount = reviews.size,
            thumbnailUrl = latestPhoto?.let(::mockMediaUrl),
            distanceMeters =
                if (latitude != null && longitude != null) {
                    MockGeo.distanceMeters(latitude, longitude, place.latitude, place.longitude)
                } else {
                    null
                },
            isFavorite = mockFavoriteStore.isFavorite(viewerId, place.placeId),
        )
    }
}
