package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 그룹 상세 (D_02 §3-1) — 생성·편집 응답(TMT-221)과 상세 화면(TMT-222)이 같이 쓴다. */
interface GroupDetailRepository : JpaRepository<GroupEntity, Long> {
    @Query(
        value = """
            SELECT g.id            AS groupId,
                   g.name          AS name,
                   g.one_line_description AS oneLineDescription,
                   g.description   AS description,
                   ma.s3_key       AS imageS3Key,
                   g.owner_id      AS ownerId,
                   g.member_count  AS memberCount,
                   g.review_count  AS reviewCount,
                   g.place_count   AS placeCount,
                   g.food_category_id AS foodCategoryId,
                   COALESCE(m.matched, 0) AS matchedSavedPlaceCount,
                   EXISTS(
                       SELECT 1 FROM group_membership gm
                       WHERE gm.group_id = g.id AND gm.user_id = CAST(:viewerId AS bigint) AND gm.status = 'ACTIVE'
                   ) AS isMember
            FROM groups g
            LEFT JOIN media_asset ma ON ma.id = g.image_asset_id
            LEFT JOIN LATERAL (
                -- 내가 저장한 가게와의 일치 수 (G12) — 저장 기준이지 찜이 아니다
                SELECT count(*) AS matched
                FROM group_place gp
                WHERE gp.group_id = g.id
                  AND gp.place_id IN (
                      SELECT DISTINCT sv.place_id FROM save sv
                      WHERE sv.user_id = CAST(:viewerId AS bigint) AND sv.deleted_at IS NULL
                  )
            ) m ON CAST(:viewerId AS bigint) IS NOT NULL
            WHERE g.id = :groupId
        """,
        nativeQuery = true,
    )
    fun findDetail(
        @Param("groupId") groupId: Long,
        @Param("viewerId") viewerId: Long?,
    ): GroupDetailRowView?

    @Query(
        value = "SELECT region_tag_id FROM group_region_tag WHERE group_id = :groupId ORDER BY region_tag_id",
        nativeQuery = true,
    )
    fun findRegionTagIds(
        @Param("groupId") groupId: Long,
    ): List<String>

    /** 상세 커버 — 공유 리뷰 최신순, 리뷰 안에서는 photo_order (G16). */
    @Query(
        value = """
            SELECT ma.s3_key AS s3Key, r.id AS reviewId
            FROM group_review_share s
            JOIN review r       ON r.id = s.review_id AND r.deleted_at IS NULL
            JOIN save_photo sp  ON sp.save_id = r.save_id
            JOIN media_asset ma ON ma.id = sp.media_asset_id
            WHERE s.group_id = :groupId
            ORDER BY r.created_at DESC, r.id DESC, sp.photo_order
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findCoverImages(
        @Param("groupId") groupId: Long,
        @Param("limit") limit: Int,
    ): List<GroupCoverImageView>

    interface GroupDetailRowView {
        fun getGroupId(): Long

        fun getName(): String

        fun getOneLineDescription(): String

        fun getDescription(): String?

        fun getImageS3Key(): String?

        fun getOwnerId(): Long

        fun getMemberCount(): Int

        fun getReviewCount(): Int

        fun getPlaceCount(): Int

        fun getFoodCategoryId(): String

        fun getMatchedSavedPlaceCount(): Long

        fun getIsMember(): Boolean
    }

    interface GroupCoverImageView {
        fun getS3Key(): String

        fun getReviewId(): Long
    }
}
