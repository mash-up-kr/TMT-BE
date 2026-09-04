package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * 마이페이지·타인 프로필 읽기 (TMT-274, 명세 v2 J·J-01). 키셋 페이징은 SQL이 하고,
 * 카드 조립(URL·라벨·표기)은 서비스가 한다 — Nearby(TMT-228)와 같은 분업이다.
 */
interface UserPageQueryPort {
    fun userExists(userId: Long): Boolean

    fun findProfileHeader(userId: Long): ProfileHeaderRow?

    /** 완성 리뷰만, (created_at, review_id) DESC 키셋 — review_user_ix와 같은 순서다. */
    fun findReviewGridRows(
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limitPlusOne: Int,
    ): List<ReviewGridRow>

    /** 가입 오래된 순 (G20) — (joined_at, group_id) ASC 키셋. matched 집계는 viewer 기준이다. */
    fun findJoinedGroupRows(
        ownerId: Long,
        viewerId: Long?,
        afterJoinedAt: Instant?,
        afterGroupId: Long?,
        limitPlusOne: Int,
    ): List<JoinedGroupRow>

    /** 찜한 최신순 (J §3-3) — (favorited_at, place_id) DESC 키셋. isFavorite는 viewer 기준이다 (F3). */
    fun findFavoritePlaceRows(
        ownerId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        afterFavoritedAt: Instant?,
        afterPlaceId: Long?,
        limitPlusOne: Int,
    ): List<FavoritePlaceRow>

    /** 발급·소비·회수 전량 — 사용자당 행 수가 티켓 수 수준이라 병합·페이징은 메모리에서 한다. */
    fun findTicketLedgerRows(userId: Long): List<TicketLedgerRow>

    /** 리뷰가 없는 살아있는 저장의 수 — 내 티켓 상단 `작성 중` 배너의 재료다 (T10·C5). */
    fun countInProgressSaves(userId: Long): Int
}

data class ProfileHeaderRow(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    val joinedGroupCount: Int,
    val favoritePlaceCount: Int,
)

data class ReviewGridRow(
    val reviewId: Long,
    val saveId: Long,
    val createdAt: Instant,
    /** 대표 사진(photo_order 최소). 사진 0장 리뷰(C4-1)는 null이다 */
    val thumbnailS3Key: String?,
    val placeId: Long,
    val placeName: String,
    val placeCategoryId: String?,
)

data class JoinedGroupRow(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    /** 최신 공유 리뷰의 첫 사진 (G16과 같은 재료) — 공유 리뷰가 없으면 null */
    val coverS3Key: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    /** 내가 저장한 매장과 겹치는 수 (G12) — viewer가 없으면 0 */
    val matchedSavedPlaceCount: Int,
    val joinedAt: Instant,
)

data class FavoritePlaceRow(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    val categoryId: String?,
    val reviewCount: Int,
    val ratingSum: Long,
    /** 최신 리뷰의 첫 사진 — 리뷰가 없으면 null */
    val thumbnailS3Key: String?,
    /** 좌표 파라미터가 없으면 null (규약 §6-3) */
    val distanceMeters: Int?,
    val favoriteByViewer: Boolean,
    val favoritedAt: Instant,
)

/** 원장 한 행의 출처 — reward_grant(발급)와 group_join_ticket의 상태 전이(소비·회수)다. */
enum class TicketLedgerKind {
    SIGNUP_GRANT,
    REVIEW_GRANT,
    GROUP_JOIN_CONSUME,
    REVIEW_DELETE_REVOKE,
}

data class TicketLedgerRow(
    val kind: TicketLedgerKind,
    /** 출처 테이블의 PK — entryId 조립 재료다 */
    val refId: Long,
    val occurredAt: Instant,
    val saveId: Long?,
    val placeId: Long?,
    val placeName: String?,
    val placeRoadAddress: String?,
    val groupId: Long?,
    val groupName: String?,
)
