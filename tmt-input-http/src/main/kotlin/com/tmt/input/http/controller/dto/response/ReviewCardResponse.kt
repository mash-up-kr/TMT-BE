package com.tmt.input.http.controller.dto.response

/** ReviewCard (명세 v2 B §1-1) — 근처 피드·가게 상세·그룹 상세·홈 피드가 같은 카드를 쓴다. */
data class ReviewCardResponse(
    val reviewId: String,
    val author: Author,
    val rating: Int,
    /** 좌표 파라미터가 없으면 null (규약 §6-3). */
    val distanceMeters: Int?,
    val photos: List<Photo>,
    /** 요약이 아직 없으면 null (A2). */
    val aiSummary: AiSummary?,
    val content: String,
    val tags: List<Tag>,
    val place: PlaceRegionSummary,
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

    data class PlaceRegionSummary(
        val placeId: String,
        val name: String,
        val regionName: String,
        val isFavorite: Boolean,
    )
}
