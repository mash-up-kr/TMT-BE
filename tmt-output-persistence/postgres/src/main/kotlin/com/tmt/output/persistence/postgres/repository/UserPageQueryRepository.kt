package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 마이페이지·타인 프로필 읽기 쿼리 (TMT-274). 키셋 술어·상관 서브쿼리는 native가 정본이다 —
 * Nearby(TMT-228)와 같은 방식.
 */
interface UserPageQueryRepository : JpaRepository<UserEntity, Long> {
    @Query(
        value = """
            SELECT u.id                AS userId,
                   u.nickname          AS nickname,
                   u.profile_image_url AS profileImageUrl,
                   (SELECT COUNT(*) FROM review r
                     WHERE r.user_id = u.id AND r.deleted_at IS NULL)          AS reviewCount,
                   (SELECT COUNT(*) FROM group_membership m
                     WHERE m.user_id = u.id AND m.status = 'ACTIVE')           AS joinedGroupCount,
                   (SELECT COUNT(*) FROM place_favorite f
                     WHERE f.user_id = u.id)                                   AS favoritePlaceCount
            FROM users u
            WHERE u.id = :userId
        """,
        nativeQuery = true,
    )
    fun findProfileHeader(
        @Param("userId") userId: Long,
    ): ProfileHeaderView?

    /** 완성 리뷰만, (created_at, id) DESC 키셋 — review_user_ix가 그대로 태운다. */
    @Query(
        value = """
            SELECT r.id         AS reviewId,
                   s.id         AS saveId,
                   r.created_at AS createdAt,
                   (SELECT ma.s3_key FROM save_photo sp
                      JOIN media_asset ma ON ma.id = sp.media_asset_id
                     WHERE sp.save_id = s.id
                     ORDER BY sp.photo_order
                     LIMIT 1)   AS thumbnailS3Key,  -- 사진 0장 리뷰(C4-1)는 NULL
                   p.id         AS placeId,
                   p.name       AS placeName,
                   p.category_id AS placeCategoryId
            FROM review r
            JOIN save s  ON s.id = r.save_id
            JOIN place p ON p.id = r.place_id
            WHERE r.user_id = :userId
              AND r.deleted_at IS NULL
              AND (CAST(:afterCreatedAt AS timestamptz) IS NULL
                   OR (r.created_at, r.id) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterReviewId AS bigint)))
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findReviewGridRows(
        @Param("userId") userId: Long,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<ReviewGridRowView>

    /**
     * 가입 오래된 순 (G20), (joined_at, group_id) ASC 키셋. 집계 3종은 groups의 파생 컬럼(D3)을 쓰고,
     * matched는 저장(Save) 기준 교집합이다 (G12) — viewer가 없으면 0.
     */
    @Query(
        value = """
            SELECT g.id                   AS groupId,
                   g.name                 AS name,
                   g.one_line_description AS oneLineDescription,
                   (SELECT ma.s3_key FROM group_review_share grs
                      JOIN review r     ON r.id = grs.review_id AND r.deleted_at IS NULL
                      JOIN save_photo sp ON sp.save_id = r.save_id
                      JOIN media_asset ma ON ma.id = sp.media_asset_id
                     WHERE grs.group_id = g.id
                     ORDER BY grs.created_at DESC, grs.review_id DESC, sp.photo_order
                     LIMIT 1)             AS coverS3Key,
                   g.member_count         AS memberCount,
                   g.review_count         AS reviewCount,
                   g.place_count          AS placeCount,
                   (SELECT COUNT(*) FROM group_place gp
                     WHERE gp.group_id = g.id
                       AND CAST(:viewerId AS bigint) IS NOT NULL
                       AND EXISTS (SELECT 1 FROM save s
                                    WHERE s.place_id = gp.place_id
                                      AND s.user_id = CAST(:viewerId AS bigint)
                                      AND s.deleted_at IS NULL)) AS matchedSavedPlaceCount,
                   m.joined_at            AS joinedAt
            FROM group_membership m
            JOIN groups g ON g.id = m.group_id
            WHERE m.user_id = :ownerId
              AND m.status = 'ACTIVE'
              AND (CAST(:afterJoinedAt AS timestamptz) IS NULL
                   OR (m.joined_at, g.id) > (CAST(:afterJoinedAt AS timestamptz), CAST(:afterGroupId AS bigint)))
            ORDER BY m.joined_at, g.id
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findJoinedGroupRows(
        @Param("ownerId") ownerId: Long,
        @Param("viewerId") viewerId: Long?,
        @Param("afterJoinedAt") afterJoinedAt: Instant?,
        @Param("afterGroupId") afterGroupId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<JoinedGroupRowView>

    /** 찜한 최신순 (J §3-3), (created_at, place_id) DESC 키셋. 거리는 좌표가 있을 때만 계산한다. */
    @Query(
        value = """
            WITH pt AS (
                SELECT CASE
                    WHEN CAST(:lat AS float8) IS NULL OR CAST(:lng AS float8) IS NULL THEN NULL
                    ELSE ST_SetSRID(ST_MakePoint(CAST(:lng AS float8), CAST(:lat AS float8)), 4326)::geography
                END AS g
            )
            SELECT p.id           AS placeId,
                   p.name         AS name,
                   p.road_address AS roadAddress,
                   p.region_name  AS regionName,
                   p.category_id  AS categoryId,
                   p.review_count AS reviewCount,
                   p.rating_sum   AS ratingSum,
                   (SELECT ma.s3_key FROM review r
                      JOIN save_photo sp ON sp.save_id = r.save_id
                      JOIN media_asset ma ON ma.id = sp.media_asset_id
                     WHERE r.place_id = p.id AND r.deleted_at IS NULL
                     ORDER BY r.created_at DESC, r.id DESC, sp.photo_order
                     LIMIT 1)    AS thumbnailS3Key,
                   CAST(round(ST_Distance(p.location, pt.g)) AS int) AS distanceMeters,
                   EXISTS(SELECT 1 FROM place_favorite vf
                           WHERE vf.user_id = CAST(:viewerId AS bigint) AND vf.place_id = p.id) AS favoriteByViewer,
                   f.created_at   AS favoritedAt
            FROM place_favorite f
            JOIN place p ON p.id = f.place_id
            CROSS JOIN pt
            WHERE f.user_id = :ownerId
              AND (CAST(:afterFavoritedAt AS timestamptz) IS NULL
                   OR (f.created_at, f.place_id) < (CAST(:afterFavoritedAt AS timestamptz), CAST(:afterPlaceId AS bigint)))
            ORDER BY f.created_at DESC, f.place_id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findFavoritePlaceRows(
        @Param("ownerId") ownerId: Long,
        @Param("viewerId") viewerId: Long?,
        @Param("lat") lat: Double?,
        @Param("lng") lng: Double?,
        @Param("afterFavoritedAt") afterFavoritedAt: Instant?,
        @Param("afterPlaceId") afterPlaceId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<FavoritePlaceRowView>

    /**
     * 발급(reward_grant)·소비·회수(group_join_ticket 상태 전이)를 한 목록으로 (T10).
     * 회수 행은 원인 리뷰를 저장하지 않아 매장 참조가 없다 — 스키마에 컬럼이 없다.
     */
    @Query(
        value = """
            SELECT 'GRANT'        AS rowKind,
                   rg.id          AS refId,
                   rg.source_type AS sourceType,
                   rg.created_at  AS occurredAt,
                   s.id           AS saveId,
                   p.id           AS placeId,
                   p.name         AS placeName,
                   p.road_address AS placeRoadAddress,
                   CAST(NULL AS bigint)  AS groupId,
                   CAST(NULL AS varchar) AS groupName
            FROM reward_grant rg
            LEFT JOIN review r ON rg.source_type = 'REVIEW' AND r.id = rg.source_id
            LEFT JOIN save s   ON s.id = r.save_id
            LEFT JOIN place p  ON p.id = r.place_id
            WHERE rg.user_id = :userId
            UNION ALL
            SELECT 'CONSUME', t.id, CAST(NULL AS varchar), t.consumed_at,
                   CAST(NULL AS bigint), CAST(NULL AS bigint), CAST(NULL AS varchar), CAST(NULL AS varchar),
                   g.id, g.name
            FROM group_join_ticket t
            LEFT JOIN groups g ON g.id = t.consumed_group_id
            WHERE t.user_id = :userId AND t.consumed_at IS NOT NULL
            UNION ALL
            SELECT 'REVOKE', t.id, CAST(NULL AS varchar), t.revoked_at,
                   CAST(NULL AS bigint), CAST(NULL AS bigint), CAST(NULL AS varchar), CAST(NULL AS varchar),
                   CAST(NULL AS bigint), CAST(NULL AS varchar)
            FROM group_join_ticket t
            WHERE t.user_id = :userId AND t.revoked_at IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun findTicketLedgerRows(
        @Param("userId") userId: Long,
    ): List<TicketLedgerRowView>

    /** 리뷰가 없는 살아있는 저장의 수 — 내 티켓 상단 `작성 중` 배너의 재료 (T10·C5). 이어쓰기 목록(GET /v1/saves)과 같은 조건이다. */
    @Query(
        value = """
            SELECT COUNT(*)
            FROM save s
            WHERE s.user_id = :userId
              AND s.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM review r WHERE r.save_id = s.id AND r.deleted_at IS NULL)
        """,
        nativeQuery = true,
    )
    fun countInProgressSaves(
        @Param("userId") userId: Long,
    ): Long

    interface ProfileHeaderView {
        fun getUserId(): Long

        fun getNickname(): String

        fun getProfileImageUrl(): String?

        fun getReviewCount(): Int

        fun getJoinedGroupCount(): Int

        fun getFavoritePlaceCount(): Int
    }

    interface ReviewGridRowView {
        fun getReviewId(): Long

        fun getSaveId(): Long

        fun getCreatedAt(): Instant

        fun getThumbnailS3Key(): String?

        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getPlaceCategoryId(): String?
    }

    interface JoinedGroupRowView {
        fun getGroupId(): Long

        fun getName(): String

        fun getOneLineDescription(): String

        fun getCoverS3Key(): String?

        fun getMemberCount(): Int

        fun getReviewCount(): Int

        fun getPlaceCount(): Int

        fun getMatchedSavedPlaceCount(): Int

        fun getJoinedAt(): Instant
    }

    interface FavoritePlaceRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getRoadAddress(): String

        fun getRegionName(): String

        fun getCategoryId(): String?

        fun getReviewCount(): Int

        fun getRatingSum(): Long

        fun getThumbnailS3Key(): String?

        fun getDistanceMeters(): Int?

        fun getFavoriteByViewer(): Boolean

        fun getFavoritedAt(): Instant
    }

    interface TicketLedgerRowView {
        fun getRowKind(): String

        fun getRefId(): Long

        fun getSourceType(): String?

        fun getOccurredAt(): Instant

        fun getSaveId(): Long?

        fun getPlaceId(): Long?

        fun getPlaceName(): String?

        fun getPlaceRoadAddress(): String?

        fun getGroupId(): Long?

        fun getGroupName(): String?
    }
}
