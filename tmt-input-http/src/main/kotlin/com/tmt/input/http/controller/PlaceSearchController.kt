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
        // 검색 조건이 하나라도 바뀌면 정렬 축까지 바뀔 수 있으므로 이전 커서는 무효다 (규약 §5-3).
        // 조건뿐 아니라 **관련도 산식이 바뀔 때도** 이전 커서는 무효다 — 커서에 실리는 sortValue가
        // 그 산식의 결과라, 옛 척도의 값으로 새 척도를 자르면 조용히 엉뚱한 페이지가 나간다.
        // 그래서 산식을 고치면 아래 판을 함께 올린다 (V2: 티어 정렬, TMT-300)
        val condition =
            CursorCondition.of(
                RELEVANCE_RANKING_VERSION,
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
                        categoryId = it.categoryId,
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
     * (sortValue, placeId) — 앞자리는 거리 미터 또는 **관련도 정수**(등급+앞매칭+유사도, TMT-300)이고,
     * 마지막 키인 placeId가 유일해 같은 점수가 경계에 걸려도 중복·누락이 없다 (TMT-178·TMT-195).
     *
     * 앞자리의 **의미가 바뀌면 [RELEVANCE_RANKING_VERSION]을 함께 올린다** — 값만 바뀌고 커서 조건이
     * 그대로면 옛 커서가 새 척도에 섞인다.
     */
    internal object PlaceSearchCursorSpec : CursorSpec<PlaceSearchKey> {
        override fun toKeys(key: PlaceSearchKey) = listOf(key.sortValue.toString(), key.placeId.toString())

        override fun fromKeys(keys: List<String>): PlaceSearchKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return PlaceSearchKey(keys[0].toInt(), keys[1].toLong())
        }
    }

    companion object {
        /**
         * 관련도 산식의 판. 커서에 실리는 `sortValue`의 **척도가 바뀌면 반드시 올린다** —
         * 올리지 않으면 배포 전에 발급된 커서가 해시 검증을 통과한 채 새 척도에 섞여
         * 에러 없이 잘못된 페이지를 준다. 올리면 `INVALID_CURSOR` 400이라 클라이언트가
         * 첫 페이지부터 다시 연다 (F §2-1).
         */
        private const val RELEVANCE_RANKING_VERSION = "PLACE_SEARCH_V2"
    }
}
