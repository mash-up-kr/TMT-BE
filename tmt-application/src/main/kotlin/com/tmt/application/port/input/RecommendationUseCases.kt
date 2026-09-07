package com.tmt.application.port.input

import java.time.Instant

/**
 * 매장 추천 (TMT-289, 명세 v2 J §5). 화면이 두 단계다 — 재료를 고르는 격자(§5-1)와 결과 카드(§5-2).
 * 격자가 표시용에서 선택 UI가 되면서(2026-09-06 개정) 고른 매장이 계약에 들어왔다.
 */
data class ReviewedPlaceKey(
    val latestReviewedAt: Instant,
    val placeId: Long,
)

/**
 * 격자 한 칸 (§5-1). 카테고리 라벨은 내리지 않는다 — 칸에 그리는 것은 이미지와 이름뿐이고,
 * [categoryId]는 사진이 없을 때 아이콘을 고르는 키로만 쓰인다.
 */
data class ReviewedPlaceView(
    val placeId: Long,
    val name: String,
    val categoryId: String?,
    /** 내가 그 매장에 쓴 최신 리뷰의 첫 사진. 사진 0장이면 null이고 화면이 카테고리 아이콘을 그린다 (C4-1·R11) */
    val thumbnailUrl: String?,
    val latestReviewedAt: Instant,
)

data class ReviewedPlaceSlice(
    val items: List<ReviewedPlaceView>,
    val hasNext: Boolean,
)

/** 내가 리뷰를 쓴 매장 — 매장 단위다. 같은 매장에 리뷰가 여러 건이어도 한 칸이다 (S6). */
interface GetReviewedPlacesUseCase {
    fun list(
        userId: Long,
        after: ReviewedPlaceKey?,
        limit: Int,
    ): ReviewedPlaceSlice
}

/**
 * 추천 요청 (§5-2). [seedPlaceIds]는 사용자가 격자에서 고른 매장이고 2~5개·중복 불가다 —
 * 하한 2는 화면의 버튼 비활성 기준이고, 상한 5가 없으면 LLM 호출 비용이 요청자 마음대로 늘어난다.
 */
data class PlaceRecommendationCommand(
    val userId: Long,
    val seedPlaceIds: List<Long>,
)

/**
 * 결과 카드 (§5-2). [summary]는 그 매장 **최신 리뷰 1건**의 `ReviewAiSummary`를 그대로 쓴다 (A3) —
 * 매장 단위 요약을 새로 만들지 않으므로 출처를 밝히는 `reviewId`가 남는다.
 */
data class PlaceRecommendationView(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    /** 결과 카드는 `주소 · 카테고리` 한 줄을 그린다 — 격자(§5-1)와 갈리는 지점이다 */
    val categoryName: String?,
    val thumbnailUrl: String?,
    val summary: Summary?,
) {
    data class Summary(
        val reviewId: Long,
        val pros: String?,
        val cons: String?,
    )
}

interface RecommendPlaceUseCase {
    fun recommend(command: PlaceRecommendationCommand): PlaceRecommendationView
}
