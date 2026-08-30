package com.tmt.input.http.controller

import com.tmt.application.port.input.GetNearbyPlacesUseCase
import com.tmt.application.port.input.GetNearbyReviewsUseCase
import com.tmt.application.port.input.NearbyPlacesRequest
import com.tmt.application.port.input.NearbyReviewKey
import com.tmt.application.port.input.NearbyReviewsRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import com.tmt.input.http.controller.dto.response.toResponse
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 근처 탐색 실구현 (TMT-228). 응답 형태·ID 표기(`rv_`·`place_`·`user_`)는 mock과 같다.
 */
@Tag(name = "근처 탐색", description = "명세 v2 — B. 근처 탐색")
@RestController
@RequestMapping("/v1/nearby")
class NearbyController(
    private val getNearbyReviewsUseCase: GetNearbyReviewsUseCase,
    private val getNearbyPlacesUseCase: GetNearbyPlacesUseCase,
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
        if (latitude == null || longitude == null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 필수이고 위경도 범위 안이어야 합니다.")
        }
        // 좌표가 바뀌면 거리 정렬이 바뀌므로 이전 커서는 무효다 (규약 §5-3)
        val condition = CursorCondition.of("NEARBY_REVIEWS", latitude, longitude)
        val after = CursorCodec.decode(NearbyCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)

        val result =
            getNearbyReviewsUseCase.get(
                NearbyReviewsRequest(
                    viewerId = userId,
                    latitude = latitude,
                    longitude = longitude,
                    after = after,
                    limit = pageLimit,
                ),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(NearbyCursorSpec, it, condition) }
            } else {
                null
            }
        return CursorPage(
            items = result.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
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
        val result =
            getNearbyPlacesUseCase.get(
                NearbyPlacesRequest(
                    north = north,
                    south = south,
                    east = east,
                    west = west,
                    centerLatitude = latitude,
                    centerLongitude = longitude,
                    query = query,
                    curationTagId = curationTagId,
                ),
            )
        return NearbyPlacesResponse(
            items =
                result.pins.map {
                    NearbyPlacesResponse.Pin(
                        placeId = PublicIds.place(it.placeId),
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        reviewCount = it.reviewCount,
                    )
                },
            truncated = result.truncated,
        )
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

    /** (distanceMeters, reviewId) — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object NearbyCursorSpec : CursorSpec<NearbyReviewKey> {
        override fun toKeys(key: NearbyReviewKey) = listOf(key.distanceMeters.toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): NearbyReviewKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return NearbyReviewKey(keys[0].toInt(), keys[1].toLong())
        }
    }
}
