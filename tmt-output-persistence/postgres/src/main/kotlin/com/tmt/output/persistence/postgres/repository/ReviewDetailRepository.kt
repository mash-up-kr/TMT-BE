package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 리뷰 단건 읽기·삭제 (TMT-226). 삭제된 리뷰는 없는 리뷰와 같다 (D6). */
interface ReviewDetailRepository : JpaRepository<ReviewEntity, Long> {
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
                   p.road_address AS placeRoadAddress,
                   p.category_id AS placeCategoryId
            FROM review r
            JOIN save s  ON s.id = r.save_id
            JOIN place p ON p.id = r.place_id
            JOIN users u ON u.id = r.user_id
            WHERE r.id = :reviewId AND r.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findReviewDetail(
        @Param("reviewId") reviewId: Long,
    ): ReviewDetailRowView?

    interface ReviewDetailRowView {
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

        fun getPlaceRoadAddress(): String

        fun getPlaceCategoryId(): String?
    }

    @Query(
        value = """
            SELECT r.id AS reviewId, r.save_id AS saveId, r.user_id AS userId,
                   r.place_id AS placeId, s.rating AS rating
            FROM review r
            JOIN save s ON s.id = r.save_id
            WHERE r.id = :reviewId AND r.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findReviewForDeletion(
        @Param("reviewId") reviewId: Long,
    ): ReviewDeletionRowView?

    interface ReviewDeletionRowView {
        fun getReviewId(): Long

        fun getSaveId(): Long

        fun getUserId(): Long

        fun getPlaceId(): Long

        fun getRating(): Int
    }

    /** 이미 지워진 행의 시각을 덮어쓰지 않게 WHERE에서 막는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ReviewEntity r SET r.deletedAt = :now WHERE r.id = :reviewId AND r.deletedAt IS NULL")
    fun softDelete(
        @Param("reviewId") reviewId: Long,
        @Param("now") now: Instant,
    ): Int
}
