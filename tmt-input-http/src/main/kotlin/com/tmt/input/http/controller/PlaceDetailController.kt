package com.tmt.input.http.controller

import com.tmt.application.port.input.GetPlaceDetailUseCase
import com.tmt.application.port.input.GetPlaceReviewsUseCase
import com.tmt.application.port.input.PlaceFavoriteUseCase
import com.tmt.application.port.input.PlaceReviewKey
import com.tmt.application.port.input.PlaceReviewsRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 가게 상세 실구현 (TMT-229) — mock을 대체한다. 응답 형태·ID 표기는 mock과 같아
 * FE 재생성이 필요 없다. 태그 문자열은 TMT-171과 함께 정리할 때까지 유지한다.
 *
 * UT2 기간에는 `tmt.mock.place-explore=true`로 내려가 있다 (TMT-250) — mock과 저장소가 갈려
 * `placeId`가 서로 다른 매장을 가리키기 때문이다. 저장·리뷰가 DB로 넘어오면 스위치를 지운다.
 */
@Tag(name = "가게 상세 (mock)", description = "명세 v2 — B §3·§4")
@ConditionalOnProperty(prefix = "tmt.mock", name = ["place-explore"], havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/v1/places/{placeId}")
class PlaceDetailController(
    private val getPlaceDetailUseCase: GetPlaceDetailUseCase,
    private val getPlaceReviewsUseCase: GetPlaceReviewsUseCase,
    private val placeFavoriteUseCase: PlaceFavoriteUseCase,
) {
    @Operation(summary = "가게 상세", description = "핀 클릭 시트와 가게 상세 상단이 같은 데이터를 쓴다. 사진은 이 매장 리뷰에서 최신순으로 파생한다 (P7).")
    @ApiErrorCodes(ErrorCode.PLACE_NOT_FOUND)
    @GetMapping
    fun placeDetail(
        @UserId userId: Long?,
        @PathVariable placeId: String,
    ): PlaceDetailResponse {
        val detail = getPlaceDetailUseCase.get(userId, PublicIds.parsePlaceId(placeId))
        return PlaceDetailResponse(
            placeId = PublicIds.place(detail.placeId),
            name = detail.name,
            categoryName = detail.categoryName,
            averageRating = detail.averageRating,
            reviewCount = detail.reviewCount,
            photos =
                detail.photos.map {
                    PlaceDetailResponse.PlacePhoto(
                        url = it.url,
                        reviewId = PublicIds.review(it.reviewId),
                    )
                },
            roadAddress = detail.roadAddress,
            latitude = detail.latitude,
            longitude = detail.longitude,
            phoneNumber = detail.phoneNumber,
            isFavorite = detail.isFavorite,
        )
    }

    @Operation(summary = "가게 상세 리뷰 목록", description = "정렬은 최신순 createdAt DESC, reviewId DESC다.")
    @ApiErrorCodes(ErrorCode.PLACE_NOT_FOUND)
    @GetMapping("/reviews")
    fun placeReviews(
        @UserId userId: Long?,
        @PathVariable placeId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<ReviewCardResponse> {
        val id = PublicIds.parsePlaceId(placeId)
        val condition = CursorCondition.of("PLACE_REVIEWS", id)
        val after = CursorCodec.decode(PlaceReviewCursorSpec, cursor, condition)

        val result =
            getPlaceReviewsUseCase.get(
                PlaceReviewsRequest(
                    viewerId = userId,
                    placeId = id,
                    viewerLatitude = latitude,
                    viewerLongitude = longitude,
                    after = after,
                    limit = PageLimit.of(limit),
                ),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(PlaceReviewCursorSpec, it, condition) }
            } else {
                null
            }
        return CursorPage(
            items = result.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    @Operation(summary = "찜", description = "멱등 토글 — 이미 찜한 매장에 다시 보내도 200이다 (F2).")
    @ApiErrorCodes(ErrorCode.PLACE_NOT_FOUND)
    @PutMapping("/favorite")
    fun addFavorite(
        @UserId userId: Long,
        @PathVariable placeId: String,
    ): FavoriteResponse {
        placeFavoriteUseCase.add(userId, PublicIds.parsePlaceId(placeId))
        return FavoriteResponse(placeId = placeId, isFavorite = true)
    }

    @Operation(summary = "찜 해제", description = "찜하지 않은 매장에 보내도 200이다 (F2).")
    @ApiErrorCodes(ErrorCode.PLACE_NOT_FOUND)
    @DeleteMapping("/favorite")
    fun removeFavorite(
        @UserId userId: Long,
        @PathVariable placeId: String,
    ): FavoriteResponse {
        placeFavoriteUseCase.remove(userId, PublicIds.parsePlaceId(placeId))
        return FavoriteResponse(placeId = placeId, isFavorite = false)
    }

    data class PlaceDetailResponse(
        val placeId: String,
        val name: String,
        val categoryName: String?,
        val averageRating: Double?,
        val reviewCount: Int,
        val photos: List<PlacePhoto>,
        val roadAddress: String,
        val latitude: Double,
        val longitude: Double,
        val phoneNumber: String?,
        val isFavorite: Boolean,
    ) {
        data class PlacePhoto(
            val url: String,
            val reviewId: String,
        )
    }

    data class FavoriteResponse(
        val placeId: String,
        val isFavorite: Boolean,
    )

    /** (createdAt, reviewId) 내림차순 — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object PlaceReviewCursorSpec : CursorSpec<PlaceReviewKey> {
        override fun toKeys(key: PlaceReviewKey) = listOf(key.createdAt.toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): PlaceReviewKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return PlaceReviewKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }
}
