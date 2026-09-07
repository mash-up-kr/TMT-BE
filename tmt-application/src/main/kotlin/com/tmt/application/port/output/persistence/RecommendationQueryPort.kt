package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * 매장 추천 읽기 (TMT-289). 격자는 **매장 단위**로 서버가 자른다 —
 * 리뷰 단위 목록을 클라이언트가 중복 제거하던 방식은 격자가 선택 UI가 되면서 깨졌다 (J §5-1).
 */
interface RecommendationQueryPort {
    /** 내가 마지막으로 리뷰한 순 — (latest_reviewed_at, place_id) DESC 키셋. 미완성 저장은 빠진다 (R8). */
    fun findReviewedPlaceRows(
        userId: Long,
        afterLatestReviewedAt: Instant?,
        afterPlaceId: Long?,
        limitPlusOne: Int,
    ): List<ReviewedPlaceRow>

    /**
     * [placeIds] 중 조회자가 실제로 리뷰를 쓴 매장의 id. 요청 검증용이라 **없는 매장과
     * 남의 매장을 가르지 않는다** — 호출자가 개수만 맞춰보고 PLACE_NOT_FOUND를 낸다 (A4).
     */
    fun findReviewedPlaceIdsAmong(
        userId: Long,
        placeIds: List<Long>,
    ): List<Long>

    /**
     * 씨앗 매장의 취향 근거 — 그 매장에 조회자가 쓴 **최신 리뷰 1건**의 별점·본문이다.
     * LLM이 "무엇을 좋아하는 사람인가"를 읽는 입력이라 남의 리뷰는 섞지 않는다.
     */
    fun findSeedPlaces(
        userId: Long,
        placeIds: List<Long>,
    ): List<SeedPlaceRow>

    /**
     * 추천 후보 — 조회자가 **아직 리뷰하지 않은** 매장이다. 씨앗 매장의 카테고리를 먼저 담고,
     * 리뷰가 많은 순으로 [limit]개까지 준다.
     *
     * **리뷰 0건 매장은 뺀다** (PR #106 리뷰). 요약도 썸네일도 없는 카드는 "추천했다"는 사실만
     * 남고 사용자가 갈 이유를 판단할 근거가 없다. 그만큼 UNAVAILABLE이 늘지만, 리뷰 있는 매장이
     * 하나도 안 남은 상황이면 추천을 안 하는 쪽이 맞는 응답이다.
     */
    fun findCandidatePlaces(
        userId: Long,
        seedPlaceIds: List<Long>,
        limit: Int,
    ): List<CandidatePlaceRow>

    /**
     * 고른 매장 1곳의 카드 재료.
     *
     * 요약은 그 매장 **최신 리뷰**의 것이고(A3), 썸네일은 **사진이 있는 리뷰 중 최신 것**의 첫 사진이다 —
     * 최신 리뷰에 사진이 없으면 그 다음 리뷰로 넘어간다. 추천 카드는 안 가본 매장을 설득하는 화면이라
     * 사진이 있는 매장인데 빈 이미지가 나오는 것보다 낫고, 기존 P7 구현(`PlaceQueryRepository`·
     * `PlaceSearchRepository`)과도 같은 모양이다.
     */
    fun findRecommendedPlace(placeId: Long): RecommendedPlaceRow?
}

data class ReviewedPlaceRow(
    val placeId: Long,
    val name: String,
    val categoryId: String?,
    val thumbnailS3Key: String?,
    val latestReviewedAt: Instant,
)

/** LLM에 넘길 후보 한 줄. 이름·카테고리·평점만 준다 — 주소·좌표는 판단에 쓰이지 않는다. */
data class CandidatePlaceRow(
    val placeId: Long,
    val name: String,
    val categoryId: String?,
    val regionName: String,
    val reviewCount: Int,
    /** 평균 별점. 리뷰가 없으면 null */
    val averageRating: Double?,
)

/** 씨앗 매장의 취향 근거. LLM 프롬프트에 들어간다. */
data class SeedPlaceRow(
    val placeId: Long,
    val name: String,
    val categoryId: String?,
    val rating: Int?,
    val content: String?,
)

data class RecommendedPlaceRow(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    val categoryId: String?,
    val thumbnailS3Key: String?,
    /** 최신 리뷰의 요약. 리뷰가 없거나 아직 요약 전이면 전부 null이다 (A2) */
    val summaryReviewId: Long?,
    val summaryPros: String?,
    val summaryCons: String?,
)
