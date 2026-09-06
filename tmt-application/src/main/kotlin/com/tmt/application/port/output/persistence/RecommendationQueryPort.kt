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
     * 리뷰가 많은 순으로 [limit]개까지 준다. 리뷰 0건 매장을 걸러내지는 않는다 —
     * 후보를 비우면 UNAVAILABLE이 되는데, 요약 없는 카드라도 내리는 편이 낫다 (A2).
     */
    fun findCandidatePlaces(
        userId: Long,
        seedPlaceIds: List<Long>,
        limit: Int,
    ): List<CandidatePlaceRow>

    /** 고른 매장 1곳의 카드 재료. 요약·썸네일은 그 매장 **최신 리뷰**에서 온다 (A3·P7). */
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
