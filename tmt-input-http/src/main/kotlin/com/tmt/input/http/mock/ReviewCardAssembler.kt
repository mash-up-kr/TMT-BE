package com.tmt.input.http.mock

/**
 * ReviewCard (명세 v2 B §1-1) — 피드·가게 상세·그룹 상세가 같은 카드를 쓴다.
 * 여기서 한 번 조립하고 각 컨트롤러가 재사용한다.
 */
class ReviewCardAssembler(
    private val placeStore: InMemoryStore<MockPlace>,
    private val favoriteStore: MockFavoriteStore,
) {
    fun assemble(
        save: MockSave,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
    ): ReviewCardResponse {
        val reviewId = requireNotNull(save.reviewId) { "완성된 리뷰만 카드가 된다 (R8)" }
        val place = placeStore.findById(save.placeId)
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
            aiSummary = MOCK_AI_SUMMARY,
            content = requireNotNull(save.content) { "리뷰는 본문이 필수다 (C4)" },
            tags =
                (save.companionTagIds + save.positivePointTagIds).map {
                    ReviewCardResponse.Tag(it, ReviewFormRules.labelOf(it))
                },
            place =
                ReviewCardResponse.PlaceSummary(
                    placeId = save.placeId,
                    name = place?.name ?: "(삭제된 매장)",
                    regionName = place?.regionName ?: "",
                    isFavorite = favoriteStore.isFavorite(viewerId, save.placeId),
                ),
            createdAt = save.createdAt.toString(),
        )
    }

    data class ReviewCardResponse(
        val reviewId: String,
        val author: MockUsers.Author,
        val rating: Int,
        val distanceMeters: Int?,
        val photos: List<Photo>,
        val aiSummary: AiSummary?,
        val content: String,
        val tags: List<Tag>,
        val place: PlaceSummary,
        val createdAt: String,
    ) {
        data class Photo(
            val photoId: String,
            val url: String,
            val order: Int,
        )

        data class AiSummary(
            val pros: String?,
            val cons: String?,
        )

        data class Tag(
            val tagId: String,
            val label: String,
        )

        data class PlaceSummary(
            val placeId: String,
            val name: String,
            val regionName: String,
            val isFavorite: Boolean,
        )
    }

    companion object {
        // AI 요약은 별도 트랜잭션에서 생성된다 (A2) — mock은 고정 요약을 내린다
        private val MOCK_AI_SUMMARY = ReviewCardResponse.AiSummary(pros = "분위기가 좋아요", cons = "가격이 좀 나가고 웨이팅이 많아요")
    }
}

/** mock 사용자 — 인증 스텁(X-User-Id)의 숫자 ID를 명세 표기(user_1)와 닉네임으로 바꾼다. */
object MockUsers {
    data class Author(
        val userId: String,
        val nickname: String,
        val profileImageUrl: String?,
    )

    fun authorOf(userId: Long): Author =
        Author(userId = "user_$userId", nickname = "미식가$userId", profileImageUrl = null)
}
