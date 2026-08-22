package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.math.roundToInt

@Tag(name = "가게 상세 (mock)", description = "명세 v2 — B §3·§4")
@RestController
@RequestMapping("/v1/places/{placeId}")
class PlaceDetailMockController(
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockFavoriteStore: MockFavoriteStore,
    private val reviewCardAssembler: ReviewCardAssembler,
) {
    @Operation(summary = "가게 상세", description = "핀 클릭 시트와 가게 상세 상단이 같은 데이터를 쓴다. 사진은 이 매장 리뷰에서 최신순으로 파생한다 (P7).")
    @GetMapping
    fun placeDetail(
        @UserId userId: Long?,
        @PathVariable placeId: String,
    ): PlaceDetailResponse {
        val place = mockPlaceStore.findById(placeId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        val reviews = reviewsOf(placeId)
        val ratings = reviews.mapNotNull { it.rating }
        return PlaceDetailResponse(
            placeId = place.placeId,
            name = place.name,
            categoryName = place.categoryName,
            averageRating = ratings.takeIf { it.isNotEmpty() }?.let { (it.average() * 10).roundToInt() / 10.0 },
            reviewCount = reviews.size,
            photos =
                reviews
                    .sortedByDescending { it.updatedAt }
                    .flatMap { save ->
                        save.photoAssetIds.map {
                            PlaceDetailResponse.PlacePhoto(
                                url = mockMediaUrl(it),
                                reviewId = save.reviewId!!,
                            )
                        }
                    }.take(MAX_PHOTOS),
            roadAddress = place.roadAddress,
            latitude = place.latitude,
            longitude = place.longitude,
            phoneNumber = place.phoneNumber,
            isFavorite = mockFavoriteStore.isFavorite(userId, placeId),
        )
    }

    @Operation(summary = "가게 상세 리뷰 목록", description = "정렬은 최신순 createdAt DESC, reviewId DESC다.")
    @GetMapping("/reviews")
    fun placeReviews(
        @UserId userId: Long?,
        @PathVariable placeId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<ReviewCardAssembler.ReviewCardResponse> {
        mockPlaceStore.findById(placeId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        val reviews =
            reviewsOf(placeId).sortedWith(
                compareByDescending<MockSave> {
                    it.createdAt
                }.thenByDescending { it.reviewId },
            )
        return MockCursor.paginate(
            reviews,
            cursor,
            limit,
        ) { reviewCardAssembler.assemble(it, userId, latitude, longitude) }
    }

    @Operation(summary = "찜", description = "멱등 토글 — 이미 찜한 매장에 다시 보내도 200이다 (F2).")
    @PutMapping("/favorite")
    fun addFavorite(
        @UserId userId: Long,
        @PathVariable placeId: String,
    ): FavoriteResponse {
        requirePlace(placeId)
        mockFavoriteStore.add(userId, placeId)
        return FavoriteResponse(placeId = placeId, isFavorite = true)
    }

    @Operation(summary = "찜 해제", description = "찜하지 않은 매장에 보내도 200이다 (F2).")
    @DeleteMapping("/favorite")
    fun removeFavorite(
        @UserId userId: Long,
        @PathVariable placeId: String,
    ): FavoriteResponse {
        requirePlace(placeId)
        mockFavoriteStore.remove(userId, placeId)
        return FavoriteResponse(placeId = placeId, isFavorite = false)
    }

    private fun requirePlace(placeId: String): MockPlace =
        mockPlaceStore.findById(placeId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)

    private fun reviewsOf(placeId: String): List<MockSave> =
        mockSaveStore.findAll().filter {
            it.placeId == placeId &&
                it.reviewId != null
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

    companion object {
        // 매장 사진은 리뷰에서 파생하므로(P7) 리뷰가 쌓이면 무한히 늘어난다. 화면에 전체보기
        // 진입점이 없어 다 쓰지도 못하므로 최신순 5장으로 자른다 — 그룹 커버(G16)와 같은 수.
        private const val MAX_PHOTOS = 5
    }
}
