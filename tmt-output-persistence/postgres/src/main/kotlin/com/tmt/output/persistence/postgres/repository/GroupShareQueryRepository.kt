package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupReviewShareEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 공유 선택 화면 조회 (H §3-1, TMT-223). */
interface GroupShareQueryRepository : JpaRepository<GroupReviewShareEntity, Long> {
    /**
     * 내 리뷰 최신순 (created_at, review_id) 내림차순 키셋 + 이 그룹 공유 여부.
     *
     * 썸네일은 리뷰의 첫 사진이고 **없을 수 있다** — `LEFT JOIN LATERAL`이어야 한다 (TMT-352).
     * INNER였을 때는 "리뷰는 사진이 필수"(C4)를 근거로 삼았는데, C4-1(TMT-268)로 사진 0장
     * 리뷰가 성립하면서 그 전제가 깨졌다. 500이 아니라 **행이 조용히 사라져** 사진 없이 쓴
     * 리뷰를 공유할 방법이 없어지고, `PUT`이 전체 교체라 이미 공유돼 있던 것까지 풀렸다.
     */
    @Query(
        value = """
            SELECT r.id         AS reviewId,
                   p.name       AS placeName,
                   thumb.s3_key AS thumbnailS3Key,
                   sv.content   AS content,
                   EXISTS(
                       SELECT 1 FROM group_review_share s
                       WHERE s.group_id = :groupId AND s.review_id = r.id
                   ) AS shared,
                   r.created_at AS createdAt
            FROM review r
            JOIN save sv ON sv.id = r.save_id
            JOIN place p ON p.id = r.place_id
            LEFT JOIN LATERAL (
                SELECT ma.s3_key
                FROM save_photo sp
                JOIN media_asset ma ON ma.id = sp.media_asset_id
                WHERE sp.save_id = r.save_id
                ORDER BY sp.photo_order
                LIMIT 1
            ) thumb ON true
            WHERE r.user_id = :userId
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
    fun findMyReviewsWithShared(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterReviewId") afterReviewId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<ReviewShareRowView>

    @Query(
        value = """
            SELECT count(*) FROM group_review_share s
            JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
            WHERE s.group_id = :groupId AND s.user_id = :userId
        """,
        nativeQuery = true,
    )
    fun countSharedByUser(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
    ): Long

    /** 목록에 남의 리뷰·없는 리뷰가 섞여 있으면 그 id들 (H §3-2). */
    @Query(
        value = """
            SELECT bad.id FROM unnest(CAST(:reviewIds AS bigint[])) AS bad(id)
            WHERE NOT EXISTS (
                SELECT 1 FROM review r
                WHERE r.id = bad.id AND r.user_id = :userId AND r.deleted_at IS NULL
            )
        """,
        nativeQuery = true,
    )
    fun findNotMine(
        @Param("userId") userId: Long,
        @Param("reviewIds") reviewIds: Array<Long>,
    ): List<Long>

    /** PUT 응답용 — countSharedByUser와 같은 기준(deleted_at 제외)이어야 GET과 값이 안 어긋난다 (PR #82 리뷰). */
    @Query(
        value = """
            SELECT s.review_id FROM group_review_share s
            JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
            WHERE s.group_id = :groupId AND s.user_id = :userId
            ORDER BY s.review_id
        """,
        nativeQuery = true,
    )
    fun findSharedReviewIds(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long,
    ): List<Long>

    interface ReviewShareRowView {
        fun getReviewId(): Long

        fun getPlaceName(): String

        /** 사진 0장 리뷰(C4-1)면 null — LEFT JOIN LATERAL이라 행은 남고 값만 빈다 */
        fun getThumbnailS3Key(): String?

        fun getContent(): String

        fun getShared(): Boolean

        fun getCreatedAt(): Instant
    }
}
