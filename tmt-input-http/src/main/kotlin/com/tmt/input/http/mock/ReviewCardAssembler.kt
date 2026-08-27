package com.tmt.input.http.mock

import com.tmt.input.http.controller.dto.response.Author
import com.tmt.input.http.controller.dto.response.ReviewCardResponse

/**
 * ReviewCard (명세 v2 B §1-1) — 피드·가게 상세·그룹 상세가 같은 카드를 쓴다.
 * 여기서 한 번 조립하고 각 컨트롤러가 재사용한다.
 */
class ReviewCardAssembler(
    private val placeStore: InMemoryStore<MockPlace>,
    private val favoriteStore: MockFavoriteStore,
    private val aiSummaryStore: MockAiSummaryStore,
) {
    fun assemble(
        save: MockSave,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        masked: Boolean = false,
    ): ReviewCardResponse {
        val reviewId = requireNotNull(save.reviewId) { "완성된 리뷰만 카드가 된다 (R8)" }
        val place = placeStore.findById(save.placeId)
        val content = requireNotNull(save.content) { "리뷰는 본문이 필수다 (C4)" }
        return ReviewCardResponse(
            reviewId = reviewId,
            author = MockUsers.authorOf(save.ownerId),
            rating = requireNotNull(save.rating) { "리뷰는 별점이 필수다 (C4)" },
            distanceMeters =
                if (latitude != null && longitude != null && place != null) {
                    MockGeo.distanceMeters(latitude, longitude, place.latitude, place.longitude)
                } else {
                    null
                },
            photos =
                save.photoAssetIds.mapIndexed { index, assetId ->
                    ReviewCardResponse.Photo(
                        photoId = "sp_${assetId.removePrefix("asset_")}",
                        url = mockMediaUrl(assetId),
                        order = index,
                    )
                },
            aiSummary =
                aiSummaryStore.find(reviewId)?.let {
                    ReviewCardResponse.AiSummary(it.pros, if (masked) null else it.cons)
                },
            content = if (masked) null else content,
            // 코드 포인트 기준 — 이모지가 든 본문에서 UTF-16 길이를 쓰면 FE가 세는 값과 어긋난다
            contentLength = content.codePointCount(0, content.length),
            tags =
                (save.companionTagIds + save.positivePointTagIds).map {
                    ReviewCardResponse.Tag(it, ReviewFormRules.labelOf(it))
                },
            place =
                ReviewCardResponse.PlaceRegionSummary(
                    placeId = save.placeId,
                    name = place?.name ?: "(삭제된 매장)",
                    regionName = place?.regionName ?: "",
                    isFavorite = favoriteStore.isFavorite(viewerId, save.placeId),
                ),
            createdAt = save.createdAt.toString(),
        )
    }
}

/** mock 사용자 — 인증 스텁(X-User-Id)의 숫자 ID를 명세 표기(user_1)와 닉네임으로 바꾼다. */
object MockUsers {
    fun authorOf(userId: Long): Author =
        Author(userId = "user_$userId", nickname = "미식가$userId", profileImageUrl = null)
}
