package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 유저 활동량 랭킹 (TMT-436). 키셋 술어·상관 서브쿼리는 native가 정본이다 —
 * 마이페이지(TMT-274)와 같은 방식.
 */
interface UserRankingQueryRepository : JpaRepository<UserEntity, Long> {
    /**
     * (reviewCount, userId) DESC 키셋. reviewCount는 키셋 술어와 정렬에 모두 쓰여 파생 테이블에서
     * 한 번만 센다 — [UserPageQueryRepository]의 `reviewCount`와 같은 정의여야 한다.
     */
    @Query(
        value = """
            SELECT t.uid       AS userId,
                   t.nickname  AS nickname,
                   t.image_url AS profileImageUrl,
                   t.s3_key    AS profileImageS3Key,
                   CAST(t.rc AS int) AS reviewCount,
                   CAST(t.sc AS int) AS sharedReviewCount
            FROM (
                SELECT u.id                AS uid,
                       u.nickname          AS nickname,
                       u.profile_image_url AS image_url,
                       ma.s3_key           AS s3_key,
                       (SELECT COUNT(*) FROM review r
                         WHERE r.user_id = u.id AND r.deleted_at IS NULL)   AS rc,
                       -- 한 리뷰를 여러 그룹에 공유해도 1이다 (share_uq는 그룹당 1행)
                       (SELECT COUNT(DISTINCT s.review_id)
                          FROM group_review_share s
                          JOIN review sr ON sr.id = s.review_id AND sr.deleted_at IS NULL
                         WHERE s.user_id = u.id)                            AS sc
                FROM users u
                LEFT JOIN media_asset ma ON ma.id = u.profile_image_asset_id
                -- 가입 미완료 계정은 랭킹에 싣지 않는다 (TMT-370)
                WHERE u.profile_completed_at IS NOT NULL
            ) t
            WHERE (CAST(:afterReviewCount AS bigint) IS NULL
                   OR (t.rc, t.uid) < (CAST(:afterReviewCount AS bigint), CAST(:afterUserId AS bigint)))
            ORDER BY t.rc DESC, t.uid DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findUserRankingRows(
        @Param("afterReviewCount") afterReviewCount: Int?,
        @Param("afterUserId") afterUserId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<UserRankingRowView>

    interface UserRankingRowView {
        fun getUserId(): Long

        fun getNickname(): String

        fun getProfileImageUrl(): String?

        fun getProfileImageS3Key(): String?

        fun getReviewCount(): Int

        fun getSharedReviewCount(): Int
    }
}
