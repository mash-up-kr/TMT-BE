package com.tmt.input.http.controller.dto.response

/** ReviewCard (명세 v2 B §1-1) — 근처 피드·가게 상세·그룹 상세·홈 피드가 같은 카드를 쓴다. */
data class ReviewCardResponse(
    val reviewId: String,
    val author: Author,
    val rating: Int,
    /** 좌표 파라미터가 없으면 null (규약 §6-3). */
    val distanceMeters: Int?,
    val photos: List<Photo>,
    /** 요약이 아직 없으면 null (A2). 미가입 그룹 리뷰 목록에서는 cons가 마스킹된다. */
    val aiSummary: AiSummary?,
    /** 미가입 그룹 리뷰 목록에서는 마스킹돼 null (D_02 §3-2). */
    val content: String?,
    /** 마스킹돼도 원본 본문의 문자 수를 내린다 — 화면이 블러 자리를 잡는다. */
    val contentLength: Int,
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
