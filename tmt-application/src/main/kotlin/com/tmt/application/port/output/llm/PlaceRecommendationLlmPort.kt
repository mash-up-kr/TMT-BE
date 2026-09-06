package com.tmt.application.port.output.llm

/**
 * 매장 추천 LLM (TMT-289). 후보를 고르는 것은 서버가 하고, **그중 하나를 고르는 것만** 맡긴다 —
 * 후보 전량을 넘기면 프롬프트가 매장 수에 비례해 늘고, 모델이 없는 매장을 지어낼 여지도 커진다.
 */
interface PlaceRecommendationLlmPort {
    /**
     * 후보 중 하나의 `placeId`를 돌려준다. 실패(모든 프로바이더 소진, 또는 후보 밖 id만 돌아옴)면
     * 예외 — 호출자가 `RECOMMENDATION_FAILED`로 바꾼다.
     */
    fun choose(request: PlaceChoiceRequest): Long
}

data class PlaceChoiceRequest(
    /** 사용자가 고른 매장과 거기 남긴 리뷰 — 취향의 근거다 */
    val seeds: List<Seed>,
    val candidates: List<Candidate>,
) {
    data class Seed(
        val name: String,
        val categoryId: String?,
        val rating: Int?,
        val content: String?,
    )

    data class Candidate(
        val placeId: Long,
        val name: String,
        val categoryId: String?,
        val regionName: String,
        val averageRating: Double?,
    )
}
