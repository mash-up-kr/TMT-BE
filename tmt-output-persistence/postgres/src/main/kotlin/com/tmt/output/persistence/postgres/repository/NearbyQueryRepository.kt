package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 근처 탐색 읽기 쿼리 (TMT-228). 공간 술어·키셋은 native가 정본이다 —
 * GiST 사용은 TMT-164에서 실데이터 141k로 EXPLAIN 검증됐다.
 */
interface NearbyQueryRepository : JpaRepository<ReviewEntity, Long> {
    /**
     * 반경 안 리뷰를 (거리, review_id) 오름차순 키셋으로 읽는다. 거리는 정수 미터(규약 §8-3).
     * 행 비교 `(distance, id) > (:d, :id)`가 같은 거리의 경계 중복·누락을 막는다 (TMT-178).
     */
    @Query(
        value = """
            WITH pt AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
            ), candidate AS (
                SELECT r.id          AS reviewId,
                       s.id          AS saveId,
                       r.created_at  AS createdAt,
                       s.rating      AS rating,
                       s.content     AS content,
                       u.id          AS authorId,
                       u.nickname    AS authorNickname,
                       u.profile_image_url AS authorProfileImageUrl,
                       p.id          AS placeId,
                       p.name        AS placeName,
                       p.region_name AS placeRegionName,
                       p.category_id AS placeCategoryId,
                       CAST(round(ST_Distance(p.location, pt.g)) AS int) AS distanceMeters,
                       EXISTS(
                           SELECT 1 FROM place_favorite f
                           WHERE f.user_id = CAST(:viewerId AS bigint) AND f.place_id = p.id
                       ) AS favorite
                FROM review r
                JOIN save s  ON s.id = r.save_id
                JOIN place p ON p.id = r.place_id
                JOIN users u ON u.id = r.user_id
                CROSS JOIN pt
                WHERE r.deleted_at IS NULL
                  AND ST_DWithin(p.location, pt.g, :radius)
            )
            SELECT * FROM candidate c
            WHERE CAST(:afterDistance AS int) IS NULL
               OR (c.distanceMeters, c.reviewId) > (CAST(:afterDistance AS int), CAST(:afterReviewId AS bigint))
            ORDER BY c.distanceMeters, c.reviewId
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findNearbyReviewRows(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radius") radius: Int,
        @Param("afterDistance") afterDistance: Int?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("viewerId") viewerId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<NearbyReviewRowView>

    interface NearbyReviewRowView {
        fun getReviewId(): Long

        fun getSaveId(): Long

        fun getCreatedAt(): Instant

        fun getRating(): Int

        fun getContent(): String

        fun getAuthorId(): Long

        fun getAuthorNickname(): String

        fun getAuthorProfileImageUrl(): String?

        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getPlaceRegionName(): String

        fun getPlaceCategoryId(): String?

        fun getDistanceMeters(): Int?

        fun getFavorite(): Boolean
    }

    @Query(
        value = """
            SELECT sp.save_id AS saveId, sp.id AS savePhotoId, m.s3_key AS s3Key,
                   sp.photo_order AS photoOrder
            FROM save_photo sp
            JOIN media_asset m ON m.id = sp.media_asset_id
            WHERE sp.save_id IN :saveIds
        """,
        nativeQuery = true,
    )
    fun findPhotoRows(
        @Param("saveIds") saveIds: Collection<Long>,
    ): List<PhotoRowView>

    interface PhotoRowView {
        fun getSaveId(): Long

        fun getSavePhotoId(): Long

        fun getS3Key(): String

        fun getPhotoOrder(): Int
    }

    /** 동행 태그 먼저, 그 안에서 노출 순서 — mock의 태그 나열 순서와 같다 */
    @Query(
        value = """
            SELECT st.save_id AS saveId, st.tag_id AS tagId, d.label AS label
            FROM save_tag st
            JOIN review_tag_definition d ON d.id = st.tag_id
            WHERE st.save_id IN :saveIds
            ORDER BY st.save_id, d.tag_type, d.display_order
        """,
        nativeQuery = true,
    )
    fun findTagRows(
        @Param("saveIds") saveIds: Collection<Long>,
    ): List<TagRowView>

    interface TagRowView {
        fun getSaveId(): Long

        fun getTagId(): String

        fun getLabel(): String
    }

    @Query(
        value = """
            SELECT a.review_id AS reviewId, a.pros AS pros, a.cons AS cons
            FROM review_ai_summary a
            WHERE a.review_id IN :reviewIds
        """,
        nativeQuery = true,
    )
    fun findSummaryRows(
        @Param("reviewIds") reviewIds: Collection<Long>,
    ): List<SummaryRowView>

    interface SummaryRowView {
        fun getReviewId(): Long

        fun getPros(): String?

        fun getCons(): String?
    }

    /**
     * bbox 안 리뷰 보유 매장(E6·E8). 검색어(E9)는 가게명(trgm)·도로명주소·카테고리 라벨
     * (상수라 서비스가 id로 변환해 CSV로 넘긴다)을 본다 — 목록과 같은 술어여야 한다.
     */
    @Query(
        value = """
            SELECT p.id AS placeId, p.name AS name,
                   ST_Y(p.location::geometry) AS latitude, ST_X(p.location::geometry) AS longitude,
                   p.category_id AS categoryId,
                   p.review_count AS reviewCount
            FROM place p
            WHERE p.review_count > 0
              AND p.location && ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography
              AND (
                    CAST(:query AS text) IS NULL
                    OR p.name ILIKE '%' || :query || '%'
                    OR p.road_address ILIKE '%' || :query || '%'
                    OR p.category_id = ANY(string_to_array(:queryCategoryCsv, ','))
                  )
              AND (CAST(:categoryId AS varchar) IS NULL OR p.category_id = :categoryId)
              AND (CAST(:regionPrefix AS text) IS NULL OR p.region_name LIKE :regionPrefix || '%')
            ORDER BY
                CASE WHEN CAST(:centerLat AS float8) IS NULL THEN 0.0
                     ELSE ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:centerLng, :centerLat), 4326)::geography)
                END,
                p.id
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findPins(
        @Param("north") north: Double,
        @Param("south") south: Double,
        @Param("east") east: Double,
        @Param("west") west: Double,
        @Param("centerLat") centerLat: Double?,
        @Param("centerLng") centerLng: Double?,
        @Param("query") query: String?,
        @Param("queryCategoryCsv") queryCategoryCsv: String,
        @Param("categoryId") categoryId: String?,
        @Param("regionPrefix") regionPrefix: String?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<PinRowView>

    interface PinRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getLatitude(): Double

        fun getLongitude(): Double

        fun getCategoryId(): String?

        fun getReviewCount(): Int
    }
}
