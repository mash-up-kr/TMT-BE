package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.PlaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 매장 추천 쿼리 (TMT-289). 격자는 매장 단위라 `DISTINCT ON (place_id)`로 리뷰를 한 줄로 접고,
 * 그 위에서 키셋을 태운다 — 애플리케이션에서 접으면 커서 경계의 페이지 크기를 보장할 수 없다.
 */
interface RecommendationQueryRepository : JpaRepository<PlaceEntity, Long> {
    /**
     * 내가 마지막으로 리뷰한 순 (J §5-1). `DISTINCT ON`의 정렬 선두는 접는 키(place_id)여야 하므로,
     * 바깥에서 (created_at, place_id) DESC로 다시 정렬한다.
     */
    @Query(
        value = """
            SELECT lr.place_id    AS placeId,
                   p.name         AS name,
                   p.category_id  AS categoryId,
                   lr.created_at  AS latestReviewedAt,
                   (SELECT ma.s3_key FROM save_photo sp
                      JOIN media_asset ma ON ma.id = sp.media_asset_id
                     WHERE sp.save_id = lr.save_id
                     ORDER BY sp.photo_order
                     LIMIT 1)     AS thumbnailS3Key
            FROM (SELECT DISTINCT ON (r.place_id)
                         r.place_id, r.created_at, r.save_id
                    FROM review r
                   WHERE r.user_id = :userId
                     AND r.deleted_at IS NULL
                   ORDER BY r.place_id, r.created_at DESC, r.id DESC) lr
            JOIN place p ON p.id = lr.place_id
            WHERE CAST(:afterLatestReviewedAt AS timestamptz) IS NULL
               OR (lr.created_at, lr.place_id)
                   < (CAST(:afterLatestReviewedAt AS timestamptz), CAST(:afterPlaceId AS bigint))
            ORDER BY lr.created_at DESC, lr.place_id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findReviewedPlaceRows(
        @Param("userId") userId: Long,
        @Param("afterLatestReviewedAt") afterLatestReviewedAt: Instant?,
        @Param("afterPlaceId") afterPlaceId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<ReviewedPlaceRowView>

    interface ReviewedPlaceRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getCategoryId(): String?

        fun getLatestReviewedAt(): Instant

        fun getThumbnailS3Key(): String?
    }

    /** 요청 검증용 (A4) — 조회자가 실제로 리뷰를 쓴 매장만 남는다. */
    @Query(
        value = """
            SELECT DISTINCT r.place_id
            FROM review r
            WHERE r.user_id = :userId
              AND r.place_id IN (:placeIds)
              AND r.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findReviewedPlaceIdsAmong(
        @Param("userId") userId: Long,
        @Param("placeIds") placeIds: List<Long>,
    ): List<Long>

    /** 씨앗 매장에 내가 쓴 최신 리뷰 1건 — LLM이 읽을 취향 근거다. */
    @Query(
        value = """
            SELECT DISTINCT ON (r.place_id)
                   r.place_id    AS placeId,
                   p.name        AS name,
                   p.category_id AS categoryId,
                   s.rating      AS rating,
                   s.content     AS content
            FROM review r
            JOIN save s  ON s.id = r.save_id
            JOIN place p ON p.id = r.place_id
            WHERE r.user_id = :userId
              AND r.place_id IN (:placeIds)
              AND r.deleted_at IS NULL
            ORDER BY r.place_id, r.created_at DESC, r.id DESC
        """,
        nativeQuery = true,
    )
    fun findSeedPlaces(
        @Param("userId") userId: Long,
        @Param("placeIds") placeIds: List<Long>,
    ): List<SeedPlaceRowView>

    interface SeedPlaceRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getCategoryId(): String?

        fun getRating(): Int?

        fun getContent(): String?
    }

    /**
     * 후보 — 조회자가 아직 리뷰하지 않은 매장이다. 씨앗과 같은 카테고리를 앞에 두고 리뷰 많은 순이다.
     *
     * 카테고리 일치는 `COALESCE(..., FALSE)`로 감싼다. `category_id`가 NULL이면 `IN`이 NULL을 주고,
     * `ORDER BY ... DESC`는 NULL을 **가장 앞에** 두므로(Postgres 기본 NULLS FIRST) 카테고리 없는
     * 매장이 1순위로 올라온다.
     */
    @Query(
        value = """
            SELECT p.id           AS placeId,
                   p.name         AS name,
                   p.category_id  AS categoryId,
                   p.region_name  AS regionName,
                   p.review_count AS reviewCount,
                   CASE WHEN p.review_count > 0
                        THEN ROUND(p.rating_sum::numeric / p.review_count, 2)
                   END            AS averageRating
            FROM place p
            WHERE NOT EXISTS (SELECT 1 FROM review r
                               WHERE r.place_id = p.id
                                 AND r.user_id = :userId
                                 AND r.deleted_at IS NULL)
            ORDER BY COALESCE(p.category_id IN (SELECT sp.category_id FROM place sp
                                                 WHERE sp.id IN (:seedPlaceIds)
                                                   AND sp.category_id IS NOT NULL), FALSE) DESC,
                     p.review_count DESC,
                     p.id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findCandidatePlaces(
        @Param("userId") userId: Long,
        @Param("seedPlaceIds") seedPlaceIds: List<Long>,
        @Param("limit") limit: Int,
    ): List<CandidatePlaceRowView>

    interface CandidatePlaceRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getCategoryId(): String?

        fun getRegionName(): String

        fun getReviewCount(): Int

        fun getAverageRating(): Double?
    }

    /**
     * 결과 카드 재료. 썸네일은 **그 매장 최신 리뷰의 첫 사진**(P7)이고, 요약은 최신 리뷰의
     * `ReviewAiSummary`다 (A3).
     *
     * `summaryReviewId`는 요약 행이 있을 때만 채워진다 — 리뷰는 있는데 요약이 아직 없는 경우가
     * `summary: null`의 두 갈래 중 하나다 (A2). 리뷰 자체가 없는 경우와 결과가 같다.
     */
    @Query(
        value = """
            SELECT p.id           AS placeId,
                   p.name         AS name,
                   p.road_address AS roadAddress,
                   p.category_id  AS categoryId,
                   (SELECT ma.s3_key
                      FROM review r2
                      JOIN save_photo sp  ON sp.save_id = r2.save_id
                      JOIN media_asset ma ON ma.id = sp.media_asset_id
                     WHERE r2.place_id = p.id AND r2.deleted_at IS NULL
                     ORDER BY r2.created_at DESC, r2.id DESC, sp.photo_order
                     LIMIT 1)     AS thumbnailS3Key,
                   sm.review_id   AS summaryReviewId,
                   sm.pros        AS summaryPros,
                   sm.cons        AS summaryCons
            FROM place p
            LEFT JOIN LATERAL (SELECT r.id
                                 FROM review r
                                WHERE r.place_id = p.id AND r.deleted_at IS NULL
                                ORDER BY r.created_at DESC, r.id DESC
                                LIMIT 1) lr ON TRUE
            LEFT JOIN review_ai_summary sm ON sm.review_id = lr.id
            WHERE p.id = :placeId
        """,
        nativeQuery = true,
    )
    fun findRecommendedPlace(
        @Param("placeId") placeId: Long,
    ): RecommendedPlaceRowView?

    interface RecommendedPlaceRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getRoadAddress(): String

        fun getCategoryId(): String?

        fun getThumbnailS3Key(): String?

        fun getSummaryReviewId(): Long?

        fun getSummaryPros(): String?

        fun getSummaryCons(): String?
    }
}
