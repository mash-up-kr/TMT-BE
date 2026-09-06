package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.PlaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 매장 검색 쿼리 (TMT-195). 술어는 근처 핀(E9)과 같고, 정렬만 두 가지다 —
 * 좌표가 오면 거리순, 없으면 매장명 유사도순.
 *
 * **정렬 키의 앞자리를 항상 정수로 만든다.** 거리는 반올림 미터(규약 §8-3),
 * 유사도는 `similarity × 1000`을 반올림한 정수다. 커서에 부동소수를 담으면
 * 문자열 왕복에서 값이 흔들려 `(score, id)` 행 비교가 경계에서 어긋날 수 있다.
 */
interface PlaceSearchRepository : JpaRepository<PlaceEntity, Long> {
    /** (distanceMeters, id) 오름차순 키셋 — 좌표가 있을 때 (B §2-2) */
    @Query(
        value = """
            WITH pt AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
            ), candidate AS (
                SELECT p.id           AS placeId,
                       p.name         AS name,
                       p.road_address AS roadAddress,
                       p.region_name  AS regionName,
                       p.category_id  AS categoryId,
                       p.rating_sum   AS ratingSum,
                       p.review_count AS reviewCount,
                       CAST(round(ST_Distance(p.location, pt.g)) AS int) AS distanceMeters,
                       EXISTS(
                           SELECT 1 FROM place_favorite f
                           WHERE f.user_id = CAST(:viewerId AS bigint) AND f.place_id = p.id
                       ) AS favorite
                FROM place p
                CROSS JOIN pt
                WHERE (
                        -- 널 가드도 :queryPattern이 정본이다 (TMT-335). :query로 가르면
                        -- 빈 검색어에서 "검색어 있음"으로 읽히는데 ILIKE NULL은 NULL이라
                        -- OR가 카테고리 한 갈래로 조용히 좁혀진다
                        CAST(:queryPattern AS text) IS NULL
                        OR p.name ILIKE :queryPattern ESCAPE '\'
                        OR p.road_address ILIKE :queryPattern ESCAPE '\'
                        OR p.category_id = ANY(string_to_array(:queryCategoryCsv, ','))
                      )
                  AND (CAST(:categoryId AS varchar) IS NULL OR p.category_id = :categoryId)
                  AND (CAST(:regionPrefix AS text) IS NULL OR p.region_name LIKE :regionPrefix || '%')
                  AND (CAST(:radius AS int) IS NULL OR ST_DWithin(p.location, pt.g, :radius))
            )
            SELECT c.*, c.distanceMeters AS sortValue FROM candidate c
            WHERE CAST(:afterSortValue AS int) IS NULL
               OR (c.distanceMeters, c.placeId) > (CAST(:afterSortValue AS int), CAST(:afterPlaceId AS bigint))
            ORDER BY c.distanceMeters, c.placeId
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun searchByDistance(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radius") radius: Int?,
        @Param("query") query: String?,
        @Param("queryPattern") queryPattern: String?,
        @Param("queryCategoryCsv") queryCategoryCsv: String,
        @Param("categoryId") categoryId: String?,
        @Param("regionPrefix") regionPrefix: String?,
        @Param("afterSortValue") afterSortValue: Int?,
        @Param("afterPlaceId") afterPlaceId: Long?,
        @Param("viewerId") viewerId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<PlaceSearchRowView>

    /**
     * (similarity×1000, id) 내림차순 키셋 — 좌표가 없을 때. 검색어가 없으면(칩만 온 경우)
     * 점수가 전부 0이라 사실상 `id DESC` 한 축이고, 그래도 tie-breaker가 유일해 경계가 안전하다.
     */
    @Query(
        value = """
            WITH candidate AS (
                SELECT p.id           AS placeId,
                       p.name         AS name,
                       p.road_address AS roadAddress,
                       p.region_name  AS regionName,
                       p.category_id  AS categoryId,
                       p.rating_sum   AS ratingSum,
                       p.review_count AS reviewCount,
                       CAST(NULL AS int) AS distanceMeters,
                       CAST(round(COALESCE(similarity(p.name, CAST(:query AS text)), 0) * 1000) AS int) AS sortValue,
                       EXISTS(
                           SELECT 1 FROM place_favorite f
                           WHERE f.user_id = CAST(:viewerId AS bigint) AND f.place_id = p.id
                       ) AS favorite
                FROM place p
                WHERE (
                        -- 널 가드도 :queryPattern이 정본이다 (TMT-335). :query로 가르면
                        -- 빈 검색어에서 "검색어 있음"으로 읽히는데 ILIKE NULL은 NULL이라
                        -- OR가 카테고리 한 갈래로 조용히 좁혀진다
                        CAST(:queryPattern AS text) IS NULL
                        OR p.name ILIKE :queryPattern ESCAPE '\'
                        OR p.road_address ILIKE :queryPattern ESCAPE '\'
                        OR p.category_id = ANY(string_to_array(:queryCategoryCsv, ','))
                      )
                  AND (CAST(:categoryId AS varchar) IS NULL OR p.category_id = :categoryId)
                  AND (CAST(:regionPrefix AS text) IS NULL OR p.region_name LIKE :regionPrefix || '%')
            )
            SELECT * FROM candidate c
            WHERE CAST(:afterSortValue AS int) IS NULL
               OR (c.sortValue, c.placeId) < (CAST(:afterSortValue AS int), CAST(:afterPlaceId AS bigint))
            ORDER BY c.sortValue DESC, c.placeId DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun searchByRelevance(
        @Param("query") query: String?,
        @Param("queryPattern") queryPattern: String?,
        @Param("queryCategoryCsv") queryCategoryCsv: String,
        @Param("categoryId") categoryId: String?,
        @Param("regionPrefix") regionPrefix: String?,
        @Param("afterSortValue") afterSortValue: Int?,
        @Param("afterPlaceId") afterPlaceId: Long?,
        @Param("viewerId") viewerId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<PlaceSearchRowView>

    interface PlaceSearchRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getRoadAddress(): String

        fun getRegionName(): String

        fun getCategoryId(): String?

        fun getRatingSum(): Long

        fun getReviewCount(): Int

        fun getDistanceMeters(): Int?

        fun getFavorite(): Boolean

        fun getSortValue(): Int
    }

    /** 매장별 최신 리뷰 사진 1장 (P7) — 리뷰 최신순, 리뷰 안에서는 photo_order 순 */
    @Query(
        value = """
            SELECT DISTINCT ON (r.place_id)
                   r.place_id AS placeId, m.s3_key AS s3Key
            FROM review r
            JOIN save_photo sp ON sp.save_id = r.save_id
            JOIN media_asset m ON m.id = sp.media_asset_id
            WHERE r.place_id IN :placeIds AND r.deleted_at IS NULL
            ORDER BY r.place_id, r.created_at DESC, r.id DESC, sp.photo_order
        """,
        nativeQuery = true,
    )
    fun findLatestPhotoRows(
        @Param("placeIds") placeIds: Collection<Long>,
    ): List<PlaceThumbnailRowView>

    interface PlaceThumbnailRowView {
        fun getPlaceId(): Long

        fun getS3Key(): String
    }
}
