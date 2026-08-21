package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "근처 탐색 (mock)", description = "명세 v2 — B. 근처 탐색")
@RestController
@RequestMapping("/v1/nearby")
class NearbyMockController(
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val reviewCardAssembler: ReviewCardAssembler,
) {
    @Operation(summary = "피드형 기본 목록", description = "내 위치 반경 1km 안의 리뷰를 거리순으로 내린다 (E1·E2·E4). 반경은 서버가 고정한다.")
    @GetMapping("/reviews")
    fun nearbyReviews(
        @UserId userId: Long?,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<ReviewCardResponse> {
        val (lat, lng) = requireCoordinates(latitude, longitude)

        val reviews =
            mockSaveStore
                .findAll()
                .filter { it.reviewId != null }
                .mapNotNull { save ->
                    val place = mockPlaceStore.findById(save.placeId) ?: return@mapNotNull null
                    save to MockGeo.distanceMeters(lat, lng, place.latitude, place.longitude)
                }.filter { (_, distance) -> distance <= MockGeo.NEARBY_RADIUS_METERS }
                .sortedWith(compareBy({ it.second }, { it.first.reviewId }))
                .map { it.first }

        return MockCursor.paginate(reviews, cursor, limit) { reviewCardAssembler.assemble(it, userId, lat, lng) }
    }

    @Operation(
        summary = "지도형 핀",
        description = "커서를 쓰지 않는다 — 지도를 움직이는 것이 페이지 이동이다. 리뷰가 1건 이상 있는 매장만, 최대 30개 (E6·E8).",
    )
    @GetMapping("/places")
    fun nearbyPlaces(
        @UserId userId: Long?,
        @RequestParam(required = false) north: Double?,
        @RequestParam(required = false) south: Double?,
        @RequestParam(required = false) east: Double?,
        @RequestParam(required = false) west: Double?,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) curationTagId: String?,
    ): NearbyPlacesResponse {
        if (north == null || south == null || east == null || west == null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "viewport 경계(north·south·east·west)는 필수입니다.")
        }

        val reviewCounts =
            mockSaveStore
                .findAll()
                .filter { it.reviewId != null }
                .groupingBy { it.placeId }
                .eachCount()

        var pins =
            mockPlaceStore
                .findAll()
                .filter { it.latitude in south..north && it.longitude in west..east }
                .filter { (reviewCounts[it.placeId] ?: 0) > 0 }
        query?.takeIf { it.isNotBlank() }?.let { q -> pins = pins.filter { it.matchesQuery(q) } }
        curationTagId?.let { chip -> pins = pins.filter(CurationPresets.matcher(chip)) }

        val truncated = pins.size > MAX_PINS
        if (truncated) {
            pins =
                if (latitude != null && longitude != null) {
                    pins
                        .sortedBy { MockGeo.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
                        .take(MAX_PINS)
                } else {
                    pins.take(MAX_PINS)
                }
        }

        return NearbyPlacesResponse(
            items =
                pins.map {
                    NearbyPlacesResponse.Pin(
                        placeId = it.placeId,
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        reviewCount = reviewCounts.getValue(it.placeId),
                    )
                },
            truncated = truncated,
        )
    }

    private fun requireCoordinates(
        latitude: Double?,
        longitude: Double?,
    ): Pair<Double, Double> {
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 필수이고 위경도 범위 안이어야 합니다.")
        }
        return latitude to longitude
    }

    data class NearbyPlacesResponse(
        val items: List<Pin>,
        val truncated: Boolean,
    ) {
        data class Pin(
            val placeId: String,
            val name: String,
            val latitude: Double,
            val longitude: Double,
            val reviewCount: Int,
        )
    }

    companion object {
        // 지도 핀 상한 (E8) — 초과 시 가까운 순 30개 + truncated
        private const val MAX_PINS = 30
    }
}
