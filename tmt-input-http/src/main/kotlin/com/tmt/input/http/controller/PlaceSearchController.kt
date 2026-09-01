package com.tmt.input.http.controller

import com.tmt.application.port.input.PlaceSearchKey
import com.tmt.application.port.input.PlaceSearchRequest
import com.tmt.application.port.input.SearchPlacesUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PlaceCardResponse
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 매장 검색 실구현 (TMT-195). 근처보기 검색·칩(B §2-2)과 리뷰 작성 1단계(F §2-1)가
 * 이 엔드포인트를 공유한다 — 응답 형태·ID 표기는 mock과 같다.
 */
@Tag(name = "매장 검색", description = "명세 v2 — B §2-2 · F §2-1")
@RestController
class PlaceSearchController(
    private val searchPlacesUseCase: SearchPlacesUseCase,
) {
    @Operation(
        summary = "매장 검색",
        description =
            "가게명·주소·음식 카테고리 태그로 찾는다 (E9). 좌표가 오면 거리순, 없으면 매장명 유사도순이다. " +
                "결과 0건은 오류가 아니다 — items: []",
    )
    @ApiErrorCodes(ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_CURSOR)
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
        // 검색 조건이 하나라도 바뀌면 정렬 축까지 바뀔 수 있으므로 이전 커서는 무효다 (규약 §5-3)
        val condition =
            CursorCondition.of(
                "PLACE_SEARCH",
                query?.takeIf { it.isNotBlank() },
                curationTagId,
                latitude,
                longitude,
                nearbyOnly == true,
            )
        val after = CursorCodec.decode(PlaceSearchCursorSpec, cursor, condition)

        val result =
            searchPlacesUseCase.search(
                PlaceSearchRequest(
                    viewerId = userId,
                    query = query,
                    curationTagId = curationTagId,
                    latitude = latitude,
                    longitude = longitude,
                    nearbyOnly = nearbyOnly ?: false,
                    after = after,
                    limit = PageLimit.of(limit),
                ),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(PlaceSearchCursorSpec, it, condition) }
            } else {
                null
            }
        return CursorPage(
            items =
                result.items.map {
                    PlaceCardResponse(
                        placeId = PublicIds.place(it.placeId),
                        name = it.name,
                        roadAddress = it.roadAddress,
                        regionName = it.regionName,
                        categoryName = it.categoryName,
                        averageRating = it.averageRating,
                        reviewCount = it.reviewCount,
                        thumbnailUrl = it.thumbnailUrl,
                        distanceMeters = it.distanceMeters,
                        isFavorite = it.isFavorite,
                    )
                },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    /**
     * (sortValue, placeId) — 앞자리는 거리 미터 또는 유사도×1000 정수이고, 마지막 키인
     * placeId가 유일해 같은 점수가 경계에 걸려도 중복·누락이 없다 (TMT-178·TMT-195).
     */
    internal object PlaceSearchCursorSpec : CursorSpec<PlaceSearchKey> {
        override fun toKeys(key: PlaceSearchKey) = listOf(key.sortValue.toString(), key.placeId.toString())

        override fun fromKeys(keys: List<String>): PlaceSearchKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return PlaceSearchKey(keys[0].toInt(), keys[1].toLong())
        }
    }
}
