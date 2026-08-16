package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Tag(name = "매장 (mock)", description = "명세 v2 — B §2-2 · F §2")
@RestController
class PlaceMockController(
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockAddressStore: InMemoryStore<MockAddress>,
    private val mockSaveStore: InMemoryStore<MockSave>,
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
        query?.takeIf { it.isNotBlank() }?.let { q ->
            results =
                results.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.roadAddress.contains(q) ||
                        it.categoryName?.contains(q) == true
                }
        }
        curationTagId?.let { chip ->
            val matches = CURATION_PRESETS[chip] ?: { false }
            results = results.filter(matches)
        }
        if (nearbyOnly == true && latitude != null && longitude != null) {
            results =
                results.filter {
                    distanceMeters(
                        latitude,
                        longitude,
                        it.latitude,
                        it.longitude,
                    ) <= NEARBY_RADIUS_METERS
                }
        }
        if (latitude != null && longitude != null) {
            results =
                results.sortedWith(
                    compareBy({ distanceMeters(latitude, longitude, it.latitude, it.longitude) }, { it.placeId }),
                )
        }

        return MockCursor.paginate(results, cursor, limit) { toPlaceCard(it, latitude, longitude) }
    }

    @Operation(summary = "매장 직접 등록", description = "같은 좌표·같은 매장명의 기존 매장이 있으면 새로 만들지 않고 200으로 그 매장을 돌려준다.")
    @PostMapping("/v1/places")
    fun createPlace(
        @UserId userId: Long,
        @RequestBody request: CreatePlaceRequest,
    ): ResponseEntity<PlaceCardResponse> {
        if (request.name.isBlank() || request.name.length > 50) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name은 1~50자여야 합니다.")
        }
        val address =
            mockAddressStore.findById(request.addressId)
                ?: throw TmtException(ErrorCode.ADDRESS_NOT_FOUND)
        val categoryName =
            request.categoryId?.let {
                ReviewFormRules.FOOD_CATEGORIES[it] ?: throw TmtException(ErrorCode.PLACE_CATEGORY_NOT_FOUND)
            }

        // 중복 매장이 쌓이면 리뷰가 흩어져 평균 별점과 지도 핀이 갈라진다 — 기존 매장을 돌려준다
        val duplicate =
            mockPlaceStore.findAll().find {
                it.name == request.name && it.latitude == address.latitude && it.longitude == address.longitude
            }
        if (duplicate != null) {
            return ResponseEntity.ok(toPlaceCard(duplicate, latitude = null, longitude = null))
        }

        val created =
            mockPlaceStore.create { id ->
                MockPlace(
                    placeId = id,
                    name = request.name,
                    roadAddress = address.roadAddress,
                    regionName = regionNameOf(address.jibunAddress),
                    categoryName = categoryName,
                    latitude = address.latitude,
                    longitude = address.longitude,
                )
            }
        return ResponseEntity
            .created(URI.create("/v1/places/${created.placeId}"))
            .body(toPlaceCard(created, latitude = null, longitude = null))
    }

    private fun toPlaceCard(
        place: MockPlace,
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
                    distanceMeters(latitude, longitude, place.latitude, place.longitude)
                } else {
                    null
                },
            isFavorite = false,
        )
    }

    data class CreatePlaceRequest(
        val name: String,
        val addressId: String,
        val categoryId: String? = null,
    )

    data class PlaceCardResponse(
        val placeId: String,
        val name: String,
        val roadAddress: String,
        val regionName: String,
        val categoryName: String?,
        val averageRating: Double?,
        val reviewCount: Int,
        val thumbnailUrl: String?,
        val distanceMeters: Int?,
        val isFavorite: Boolean,
    )

    companion object {
        private const val NEARBY_RADIUS_METERS = 1_000

        // 칩은 검색 조건 프리셋으로 동작한다 (E12) — mock은 지역·카테고리 매칭으로 흉내낸다
        private val CURATION_PRESETS: Map<String, (MockPlace) -> Boolean> =
            mapOf(
                "curation_euljiro_yajang" to { p -> p.regionName.startsWith("중구") },
                "curation_ganmaek" to { p -> p.categoryName == "주점" },
                "curation_butteotteok" to { p -> p.categoryName == "카페·디저트" },
                "curation_lamb" to { p -> p.categoryName == "고기·구이" },
            )

        /** 지번주소 "서울 양천구 신정동 948-1" → "양천구 신정동" */
        private fun regionNameOf(jibunAddress: String): String =
            jibunAddress
                .split(" ")
                .drop(1)
                .take(2)
                .joinToString(" ")

        private fun distanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Int {
            val earthRadius = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a =
                sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return (earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
        }
    }
}
