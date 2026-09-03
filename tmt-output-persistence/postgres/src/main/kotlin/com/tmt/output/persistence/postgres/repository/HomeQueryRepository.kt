package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 홈 읽기 쿼리 (TMT-230). 공유 리뷰의 중복 제거(G19)와 추천 정렬(G17)이 SQL 안에서 끝난다 —
 * 애플리케이션에서 합치면 커서 경계에서 페이지 크기를 보장할 수 없다.
 */
interface HomeQueryRepository : JpaRepository<ReviewEntity, Long> {
    @Query(value = "SELECT u.nickname FROM users u WHERE u.id = :userId", nativeQuery = true)
    fun findNickname(
        @Param("userId") userId: Long,
    ): String?

    /** 가입 순서(오래된 순). joined_at 동률은 group_id로 가른다 (A §2) */
    @Query(
        value = """
            SELECT g.id AS groupId, g.name AS name, ma.s3_key AS imageS3Key
            FROM group_membership gm
            JOIN groups g ON g.id = gm.group_id
            LEFT JOIN media_asset ma ON ma.id = g.image_asset_id
            WHERE gm.user_id = :userId AND gm.status = 'ACTIVE'
            ORDER BY gm.joined_at, g.id
        """,
        nativeQuery = true,
    )
    fun findMyGroups(
        @Param("userId") userId: Long,
    ): List<MyGroupRowView>

    interface MyGroupRowView {
        fun getGroupId(): Long

        fun getName(): String

        fun getImageS3Key(): String?
    }

    /**
     * 추천순(G17) — 내 저장 매장과 겹치는 수 → 가입자 수 → group_id. 이미 가입한 그룹은 뺀다 (A §5-3).
     * 커버는 최신 공유 리뷰의 첫 사진이다 (G16) — LATERAL로 그룹당 1행만 뽑아 N+1을 피한다.
     */
    @Query(
        value = """
            SELECT g.id   AS groupId,
                   g.name AS name,
                   g.one_line_description AS oneLineDescription,
                   g.member_count AS memberCount,
                   g.review_count AS reviewCount,
                   g.place_count  AS placeCount,
                   matched.cnt    AS matchedSavedPlaceCount,
                   cover.s3_key   AS coverS3Key
            FROM groups g
            JOIN LATERAL (
                SELECT count(*) AS cnt
                FROM group_place gp
                WHERE gp.group_id = g.id
                  AND EXISTS (
                      SELECT 1 FROM save s
                      WHERE s.user_id = :userId AND s.place_id = gp.place_id AND s.deleted_at IS NULL
                  )
            ) matched ON true
            LEFT JOIN LATERAL (
                SELECT ma.s3_key
                FROM group_review_share grs
                JOIN review r      ON r.id = grs.review_id AND r.deleted_at IS NULL
                JOIN save_photo sp ON sp.save_id = r.save_id
                JOIN media_asset ma ON ma.id = sp.media_asset_id
                WHERE grs.group_id = g.id
                ORDER BY r.created_at DESC, r.id DESC, sp.photo_order
                LIMIT 1
            ) cover ON true
            WHERE NOT EXISTS (
                SELECT 1 FROM group_membership gm
                WHERE gm.group_id = g.id AND gm.user_id = :userId AND gm.status = 'ACTIVE'
            )
            ORDER BY matched.cnt DESC, g.member_count DESC, g.id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecommendedGroups(
        @Param("userId") userId: Long,
        @Param("limit") limit: Int,
    ): List<GroupCardRowView>

    interface GroupCardRowView {
        fun getGroupId(): Long

        fun getName(): String

        fun getOneLineDescription(): String

        fun getMemberCount(): Int

        fun getReviewCount(): Int

        fun getPlaceCount(): Int

        fun getMatchedSavedPlaceCount(): Int

        fun getCoverS3Key(): String?
    }

    /**
     * 가입 그룹에 공유된 리뷰를 (거리, review_id) 오름차순 키셋으로 읽는다.
     * 공유를 EXISTS로 보므로 같은 리뷰가 여러 그룹에 있어도 행이 늘지 않는다 (G19).
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
                           WHERE f.user_id = :userId AND f.place_id = p.id
                       ) AS favorite
                FROM review r
                JOIN save s  ON s.id = r.save_id
                JOIN place p ON p.id = r.place_id
                JOIN users u ON u.id = r.user_id
                CROSS JOIN pt
                WHERE r.deleted_at IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM group_review_share grs
                      JOIN group_membership gm
                        ON gm.group_id = grs.group_id AND gm.user_id = :userId AND gm.status = 'ACTIVE'
                      WHERE grs.review_id = r.id
                  )
            )
            SELECT * FROM candidate c
            WHERE CAST(:afterDistance AS int) IS NULL
               OR (c.distanceMeters, c.reviewId) > (CAST(:afterDistance AS int), CAST(:afterReviewId AS bigint))
            ORDER BY c.distanceMeters, c.reviewId
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findFeedRowsByDistance(
        @Param("userId") userId: Long,
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("afterDistance") afterDistance: Int?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<HomeFeedRowView>

    /** 좌표가 없을 때 — (created_at, review_id) 내림차순 키셋. distanceMeters는 null이다 (규약 §6-3) */
    @Query(
        value = """
            WITH candidate AS (
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
                       CAST(NULL AS int) AS distanceMeters,
                       EXISTS(
                           SELECT 1 FROM place_favorite f
                           WHERE f.user_id = :userId AND f.place_id = p.id
                       ) AS favorite
                FROM review r
                JOIN save s  ON s.id = r.save_id
                JOIN place p ON p.id = r.place_id
                JOIN users u ON u.id = r.user_id
                WHERE r.deleted_at IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM group_review_share grs
                      JOIN group_membership gm
                        ON gm.group_id = grs.group_id AND gm.user_id = :userId AND gm.status = 'ACTIVE'
                      WHERE grs.review_id = r.id
                  )
            )
            SELECT * FROM candidate c
            WHERE CAST(:afterCreatedAt AS timestamptz) IS NULL
               OR (c.createdAt, c.reviewId) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterReviewId AS bigint))
            ORDER BY c.createdAt DESC, c.reviewId DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findFeedRowsByRecency(
        @Param("userId") userId: Long,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<HomeFeedRowView>

    interface HomeFeedRowView {
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
}
