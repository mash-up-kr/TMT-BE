package com.tmt.input.http.mock

import com.tmt.input.http.controller.dto.response.PlaceCardResponse
import kotlin.math.roundToInt

/** PlaceCard (명세 v2 B §1-2) — 매장 검색·좋아요 탭이 같은 카드를 쓴다. */
class PlaceCardAssembler(
    private val mockMediaUrls: MockMediaUrls,
    private val saveStore: InMemoryStore<MockSave>,
    private val favoriteStore: MockFavoriteStore,
) {
    fun assemble(
        place: MockPlace,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
    ): PlaceCardResponse {
        val reviews = saveStore.findAll().filter { it.placeId == place.placeId && it.reviewId != null }
        val ratings = reviews.mapNotNull { it.rating }
        val latestPhoto = reviews.maxByOrNull { it.createdAt }?.photoAssetIds?.firstOrNull()
        return PlaceCardResponse(
            placeId = place.placeId,
            name = place.name,
            roadAddress = place.roadAddress,
            regionName = place.regionName,
            categoryName = place.categoryName,
            averageRating = ratings.takeIf { it.isNotEmpty() }?.let { (it.average() * 10).roundToInt() / 10.0 },
            reviewCount = reviews.size,
            thumbnailUrl = latestPhoto?.let(mockMediaUrls::urlOf),
            distanceMeters =
                if (latitude != null && longitude != null) {
                    MockGeo.distanceMeters(latitude, longitude, place.latitude, place.longitude)
                } else {
                    null
                },
            isFavorite = favoriteStore.isFavorite(viewerId, place.placeId),
        )
    }
}
