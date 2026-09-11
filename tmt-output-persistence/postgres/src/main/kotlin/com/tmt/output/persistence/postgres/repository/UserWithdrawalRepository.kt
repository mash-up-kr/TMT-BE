package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 회원탈퇴의 삭제 문장들 (TMT-409). 운영에서 손으로 쓰던 순서(`scripts/ops/delete-user.sh`)를 옮긴 것이다.
 *
 * users를 참조하는 FK가 전부 NO ACTION이라 **자식부터** 지운다. 순환이 하나 있다 —
 * `users.profile_image_asset_id`가 `media_asset`을 참조하므로(V7) 에셋을 지우기 전에 끊어야 한다.
 */
interface UserWithdrawalRepository : JpaRepository<UserEntity, Long> {
    @Query(value = "SELECT id FROM groups WHERE owner_id = :userId", nativeQuery = true)
    fun findOwnedGroupIds(
        @Param("userId") userId: Long,
    ): List<Long>

    @Query(
        value = "SELECT group_id FROM group_membership WHERE user_id = :userId AND status = 'ACTIVE'",
        nativeQuery = true,
    )
    fun findActiveMembershipGroupIds(
        @Param("userId") userId: Long,
    ): List<Long>

    /**
     * 별점은 save에 있다 (P9). 삭제된 리뷰는 이미 집계에서 빠져 있어 되돌릴 것이 없다.
     * 별점이 비어 있어도 리뷰는 세야 하므로(C4상 나올 일은 없다) 0으로 본다 — review_count는 차감되고
     * rating_sum은 그대로 남는다.
     */
    @Query(
        value = """
            SELECT r.place_id AS placeId, coalesce(sv.rating, 0) AS rating
            FROM review r
            JOIN save sv ON sv.id = r.save_id
            WHERE r.user_id = :userId
              AND r.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findReviewRatings(
        @Param("userId") userId: Long,
    ): List<ReviewRatingView>

    /** 내가 공유한 그룹 + 남이 내 리뷰를 공유한 그룹. */
    @Query(
        value = """
            SELECT DISTINCT s.group_id
            FROM group_review_share s
            WHERE s.user_id = :userId
               OR s.review_id IN (SELECT id FROM review WHERE user_id = :userId)
        """,
        nativeQuery = true,
    )
    fun findGroupIdsAffectedByShares(
        @Param("userId") userId: Long,
    ): List<Long>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_review_share WHERE group_id IN (:groupIds)", nativeQuery = true)
    fun deleteSharesByGroupIds(
        @Param("groupIds") groupIds: List<Long>,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_place WHERE group_id IN (:groupIds)", nativeQuery = true)
    fun deleteGroupPlacesByGroupIds(
        @Param("groupIds") groupIds: List<Long>,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_region_tag WHERE group_id IN (:groupIds)", nativeQuery = true)
    fun deleteRegionTagsByGroupIds(
        @Param("groupIds") groupIds: List<Long>,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_membership WHERE group_id IN (:groupIds)", nativeQuery = true)
    fun deleteMembershipsByGroupIds(
        @Param("groupIds") groupIds: List<Long>,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM groups WHERE id IN (:groupIds)", nativeQuery = true)
    fun deleteGroups(
        @Param("groupIds") groupIds: List<Long>,
    )

    /** 남이 내 리뷰를 공유한 행까지 끊어야 review 삭제가 FK에 걸리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            DELETE FROM group_review_share
            WHERE user_id = :userId
               OR review_id IN (SELECT id FROM review WHERE user_id = :userId)
        """,
        nativeQuery = true,
    )
    fun deleteSharesOfUser(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM review_ai_summary WHERE review_id IN (SELECT id FROM review WHERE user_id = :userId)",
        nativeQuery = true,
    )
    fun deleteReviewSummaries(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review WHERE user_id = :userId", nativeQuery = true)
    fun deleteReviews(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM save_tag WHERE save_id IN (SELECT id FROM save WHERE user_id = :userId)",
        nativeQuery = true,
    )
    fun deleteSaveTags(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM save_photo WHERE save_id IN (SELECT id FROM save WHERE user_id = :userId)",
        nativeQuery = true,
    )
    fun deleteSavePhotos(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM save WHERE user_id = :userId", nativeQuery = true)
    fun deleteSaves(
        @Param("userId") userId: Long,
    )

    /** 티켓이 근거(reward_grant)를 참조하므로 티켓부터. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_join_ticket WHERE user_id = :userId", nativeQuery = true)
    fun deleteTickets(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM reward_grant WHERE user_id = :userId", nativeQuery = true)
    fun deleteRewardGrants(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM group_membership WHERE user_id = :userId", nativeQuery = true)
    fun deleteMembershipsOfUser(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM place_favorite WHERE user_id = :userId", nativeQuery = true)
    fun deleteFavorites(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM idempotency_key WHERE user_id = :userId", nativeQuery = true)
    fun deleteIdempotencyKeys(
        @Param("userId") userId: Long,
    )

    /**
     * V7의 순환을 끊는다 — 이 줄이 없으면 다음 문장이 FK 위반으로 멈춘다.
     * 남이 이 사용자의 에셋을 프로필로 쓰고 있어도 같이 끊는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE users SET profile_image_asset_id = NULL
            WHERE profile_image_asset_id IN (SELECT id FROM media_asset WHERE owner_id = :userId)
        """,
        nativeQuery = true,
    )
    fun clearProfileImage(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM media_asset WHERE owner_id = :userId", nativeQuery = true)
    fun deleteMediaAssets(
        @Param("userId") userId: Long,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    fun deleteUser(
        @Param("userId") userId: Long,
    )
}

interface ReviewRatingView {
    fun getPlaceId(): Long

    fun getRating(): Int
}
