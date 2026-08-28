package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.PlaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 가게 상세·리뷰 목록·찜 쿼리 (TMT-229). */
interface PlaceQueryRepository : JpaRepository<PlaceEntity, Long> {
    @Query(
        value = """
            SELECT p.id AS placeId, p.name AS name, p.category_id AS categoryId,
                   p.rating_sum AS ratingSum, p.review_count AS reviewCount,
                   p.road_address AS roadAddress,
                   ST_Y(p.location::geometry) AS latitude, ST_X(p.location::geometry) AS longitude,
                   p.phone_number AS phoneNumber,
                   EXISTS(
                       SELECT 1 FROM place_favorite f
                       WHERE f.user_id = CAST(:viewerId AS bigint) AND f.place_id = p.id
                   ) AS favorite
            FROM place p
            WHERE p.id = :placeId
        """,
        nativeQuery = true,
    )
    fun findDetail(
        @Param("placeId") placeId: Long,
        @Param("viewerId") viewerId: Long?,
    ): PlaceDetailRowView?

    interface PlaceDetailRowView {
        fun getPlaceId(): Long

        fun getName(): String

        fun getCategoryId(): String?

        fun getRatingSum(): Long

        fun getReviewCount(): Int

        fun getRoadAddress(): String

        fun getLatitude(): Double

        fun getLongitude(): Double

        fun getPhoneNumber(): String?

        fun getFavorite(): Boolean
    }

    /** 대표 사진 — 리뷰 최신순(P7), 리뷰 안에서는 photo_order 순 */
    @Query(
        value = """
            SELECT m.s3_key AS s3Key, r.id AS reviewId
            FROM review r
            JOIN save_photo sp ON sp.save_id = r.save_id
            JOIN media_asset m ON m.id = sp.media_asset_id
            WHERE r.place_id = :placeId AND r.deleted_at IS NULL
            ORDER BY r.created_at DESC, r.id DESC, sp.photo_order
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentPhotos(
        @Param("placeId") placeId: Long,
        @Param("limit") limit: Int,
    ): List<PlacePhotoRowView>

    interface PlacePhotoRowView {
        fun getS3Key(): String

        fun getReviewId(): Long
    }

    /** 최신순 (created_at, review_id) 내림차순 키셋 — review_place_ix가 이 모양이다 (B §3-2) */
    @Query(
        value = """
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
            FROM review r
            JOIN save s  ON s.id = r.save_id
            JOIN place p ON p.id = r.place_id
            JOIN users u ON u.id = r.user_id
            WHERE r.place_id = :placeId
              AND r.deleted_at IS NULL
              AND (
                    CAST(:afterCreatedAt AS timestamptz) IS NULL
                    OR (r.created_at, r.id) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterReviewId AS bigint))
                  )
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findPlaceReviewRows(
        @Param("placeId") placeId: Long,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("viewerId") viewerId: Long?,
        @Param("viewerLat") viewerLat: Double?,
        @Param("viewerLng") viewerLng: Double?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<NearbyQueryRepository.NearbyReviewRowView>

    /** 찜 — UNIQUE 충돌은 무시한다 (F2 멱등) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO place_favorite (user_id, place_id)
            VALUES (:userId, :placeId)
            ON CONFLICT (user_id, place_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun addFavorite(
        @Param("userId") userId: Long,
        @Param("placeId") placeId: Long,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM place_favorite WHERE user_id = :userId AND place_id = :placeId",
        nativeQuery = true,
    )
    fun removeFavorite(
        @Param("userId") userId: Long,
        @Param("placeId") placeId: Long,
    ): Int
}
