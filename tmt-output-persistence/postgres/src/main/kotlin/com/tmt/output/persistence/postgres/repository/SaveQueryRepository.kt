package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.SaveEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 저장 읽기 (TMT-225). 키셋·행 비교가 필요해 native가 정본이다. */
interface SaveQueryRepository : JpaRepository<SaveEntity, Long> {
    @Query(
        value = """
            SELECT s.id           AS saveId,
                   s.user_id      AS userId,
                   r.id           AS reviewId,
                   s.rating       AS rating,
                   s.content      AS content,
                   s.created_at   AS createdAt,
                   p.id           AS placeId,
                   p.name         AS placeName,
                   p.road_address AS placeRoadAddress,
                   p.category_id  AS placeCategoryId,
                   a.pros         AS aiSummaryPros,
                   a.cons         AS aiSummaryCons
            FROM save s
            JOIN place p ON p.id = s.place_id
            LEFT JOIN review r ON r.save_id = s.id AND r.deleted_at IS NULL
            LEFT JOIN review_ai_summary a ON a.review_id = r.id
            WHERE s.id = :saveId AND s.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findSaveRow(
        @Param("saveId") saveId: Long,
    ): SaveRowView?

    interface SaveRowView {
        fun getSaveId(): Long

        fun getUserId(): Long

        fun getReviewId(): Long?

        fun getRating(): Int?

        fun getContent(): String?

        fun getCreatedAt(): Instant

        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getPlaceRoadAddress(): String

        fun getPlaceCategoryId(): String?

        fun getAiSummaryPros(): String?

        fun getAiSummaryCons(): String?
    }

    @Query(
        value = """
            SELECT sp.id AS savePhotoId, sp.media_asset_id AS mediaAssetId,
                   m.s3_key AS s3Key, sp.photo_order AS photoOrder
            FROM save_photo sp
            JOIN media_asset m ON m.id = sp.media_asset_id
            WHERE sp.save_id = :saveId
            ORDER BY sp.photo_order
        """,
        nativeQuery = true,
    )
    fun findPhotoRows(
        @Param("saveId") saveId: Long,
    ): List<SavePhotoRowView>

    interface SavePhotoRowView {
        fun getSavePhotoId(): Long

        fun getMediaAssetId(): Long

        fun getS3Key(): String

        fun getPhotoOrder(): Int
    }

    /** 동행 태그 먼저, 그 안에서 노출 순서 */
    @Query(
        value = """
            SELECT st.tag_id AS tagId, d.label AS label
            FROM save_tag st
            JOIN review_tag_definition d ON d.id = st.tag_id
            WHERE st.save_id = :saveId
            ORDER BY d.tag_type, d.display_order
        """,
        nativeQuery = true,
    )
    fun findTagRows(
        @Param("saveId") saveId: Long,
    ): List<SaveTagRowView>

    interface SaveTagRowView {
        fun getTagId(): String

        fun getLabel(): String
    }

    /**
     * 미완성 저장만 (C5·R8) — 리뷰가 붙은 저장은 이어쓰기 대상이 아니다.
     * 행 비교 `(updated_at, id) < (:after…)`가 같은 시각의 경계 중복·누락을 막는다 (TMT-178).
     */
    @Query(
        value = """
            SELECT s.id           AS saveId,
                   s.updated_at   AS updatedAt,
                   p.id           AS placeId,
                   p.name         AS placeName,
                   p.road_address AS placeRoadAddress,
                   (SELECT m.s3_key
                      FROM save_photo sp
                      JOIN media_asset m ON m.id = sp.media_asset_id
                     WHERE sp.save_id = s.id
                     ORDER BY sp.photo_order
                     LIMIT 1)     AS thumbnailS3Key
            FROM save s
            JOIN place p ON p.id = s.place_id
            WHERE s.user_id = :userId
              AND s.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM review r WHERE r.save_id = s.id AND r.deleted_at IS NULL
              )
              AND (CAST(:afterUpdatedAt AS timestamptz) IS NULL
                   OR (s.updated_at, s.id) < (CAST(:afterUpdatedAt AS timestamptz), CAST(:afterSaveId AS bigint)))
            ORDER BY s.updated_at DESC, s.id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findMySaveRows(
        @Param("userId") userId: Long,
        @Param("afterUpdatedAt") afterUpdatedAt: Instant?,
        @Param("afterSaveId") afterSaveId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<MySaveRowView>

    interface MySaveRowView {
        fun getSaveId(): Long

        fun getUpdatedAt(): Instant

        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getPlaceRoadAddress(): String

        fun getThumbnailS3Key(): String?
    }
}
