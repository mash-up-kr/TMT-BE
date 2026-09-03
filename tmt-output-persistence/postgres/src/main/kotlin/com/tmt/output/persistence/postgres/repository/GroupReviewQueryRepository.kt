package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupReviewShareEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 그룹에 공유된 리뷰 목록 (D_02 §3-2, TMT-222). */
interface GroupReviewQueryRepository : JpaRepository<GroupReviewShareEntity, Long> {
    @Query(
        value = "SELECT EXISTS(SELECT 1 FROM groups g WHERE g.id = :groupId)",
        nativeQuery = true,
    )
    fun existsGroup(
        @Param("groupId") groupId: Long,
    ): Boolean

    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1 FROM group_membership gm
                WHERE gm.group_id = :groupId AND gm.user_id = :userId AND gm.status = 'ACTIVE'
            )
        """,
        nativeQuery = true,
    )
    fun isMember(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
    ): Boolean

    /**
     * 리뷰 최신순 (created_at, review_id) 내림차순 키셋 — mock과 같은 정렬이다.
     * share_gate_ix는 공유 시각 기준이라 이 정렬을 못 태우지만, 그룹당 공유는 수백 규모라 감수한다.
     */
    @Query(
        value = """
            SELECT r.id          AS reviewId,
                   sv.id         AS saveId,
                   r.created_at  AS createdAt,
                   sv.rating     AS rating,
                   sv.content    AS content,
                   u.id          AS authorId,
                   u.nickname    AS authorNickname,
                   u.profile_image_url AS authorProfileImageUrl,
                   p.id          AS placeId,
                   p.name        AS placeName,
                   p.region_name AS placeRegionName,
                   p.category_id AS placeCategoryId,
                   CASE WHEN CAST(:viewerLat AS float8) IS NULL THEN NULL
                        ELSE CAST(round(ST_Distance(
                                 p.location,
                                 ST_SetSRID(ST_MakePoint(:viewerLng, :viewerLat), 4326)::geography
                             )) AS int)
                   END AS distanceMeters,
                   EXISTS(
                       SELECT 1 FROM place_favorite f
                       WHERE f.user_id = CAST(:viewerId AS bigint) AND f.place_id = p.id
                   ) AS favorite
            FROM group_review_share s
            JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
            JOIN save sv  ON sv.id = r.save_id
            JOIN place p  ON p.id = r.place_id
            JOIN users u  ON u.id = r.user_id
            WHERE s.group_id = :groupId
              AND (
                    CAST(:afterCreatedAt AS timestamptz) IS NULL
                    OR (r.created_at, r.id) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterReviewId AS bigint))
                  )
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findSharedReviewRows(
        @Param("groupId") groupId: Long,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("viewerId") viewerId: Long?,
        @Param("viewerLat") viewerLat: Double?,
        @Param("viewerLng") viewerLng: Double?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<NearbyQueryRepository.NearbyReviewRowView>
}
