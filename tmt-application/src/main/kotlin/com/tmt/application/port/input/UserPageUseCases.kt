package com.tmt.application.port.input

import java.time.Instant

/**
 * 마이페이지·타인 프로필 (TMT-274, 명세 v2 J·J-01). 두 화면은 탭 3종의 항목·정렬이 같고,
 * 상단과 소유자 전용 필드(email·티켓·saveId)만 다르다 (J-01 §6-1) — 노출 가공은 컨트롤러가 한다.
 */
data class UserProfileView(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    val joinedGroupCount: Int,
    val favoritePlaceCount: Int,
    /** 본인 조회에만 담긴다 (U7) */
    val availableTicketCount: Int?,
    /** users에 이메일이 없다 — 동의항목을 수집하지 않아 당분간 항상 null (J §2) */
    val email: String?,
)

interface GetUserProfileUseCase {
    fun getMine(userId: Long): UserProfileView

    /** 없는 사용자는 USER_NOT_FOUND */
    fun getOther(targetUserId: Long): UserProfileView
}

data class ReviewGridKey(
    val createdAt: Instant,
    val reviewId: Long,
)

data class ReviewGridItemView(
    val reviewId: Long,
    val saveId: Long,
    val thumbnailUrl: String,
    val placeId: Long,
    val placeName: String,
    val placeCategoryName: String?,
    val createdAt: Instant,
)

data class ReviewGridSlice(
    val items: List<ReviewGridItemView>,
    val hasNext: Boolean,
)

interface GetUserReviewGridUseCase {
    fun list(
        targetUserId: Long,
        after: ReviewGridKey?,
        limit: Int,
    ): ReviewGridSlice
}

data class JoinedGroupKey(
    val joinedAt: Instant,
    val groupId: Long,
)

data class GroupCardView(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    val coverImageUrl: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val matchedSavedPlaceCount: Int,
    val joinedAt: Instant,
)

data class GroupCardSlice(
    val items: List<GroupCardView>,
    val hasNext: Boolean,
)

interface GetUserGroupsUseCase {
    fun list(
        targetUserId: Long,
        viewerId: Long?,
        after: JoinedGroupKey?,
        limit: Int,
    ): GroupCardSlice
}

data class FavoriteKey(
    val favoritedAt: Instant,
    val placeId: Long,
)

data class FavoritePlaceView(
    val placeId: Long,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    val categoryName: String?,
    /** 리뷰가 없으면 null. 소수 첫째 자리 (P9·규약 §8-3) */
    val averageRating: Double?,
    val reviewCount: Int,
    val thumbnailUrl: String?,
    val distanceMeters: Int?,
    val isFavorite: Boolean,
    val favoritedAt: Instant,
)

data class FavoriteSlice(
    val items: List<FavoritePlaceView>,
    val hasNext: Boolean,
)

interface GetUserFavoritesUseCase {
    fun list(
        targetUserId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        after: FavoriteKey?,
        limit: Int,
    ): FavoriteSlice
}

data class TicketHistoryKey(
    val occurredAt: Instant,
    val entryId: String,
)

/** 이력 행의 종류 (J §4-1). 저장에서 파생하는 SAVE_IN_PROGRESS가 원장 종류보다 하나 많다. */
enum class TicketHistoryItemType {
    SAVE_IN_PROGRESS,
    SIGNUP_REWARD,
    REVIEW_REWARD,
    REVIEW_DELETE_REVOKE,
    GROUP_JOIN,
}

data class TicketHistoryItemView(
    val entryId: String,
    val type: TicketHistoryItemType,
    /** null이면 티켓이 오간 적 없는 행 — 화면이 `작성 중` 배지를 그린다 (T10) */
    val amount: Int?,
    val saveId: Long?,
    val place: PlaceRefView?,
    val group: GroupRefView?,
    val occurredAt: Instant,
) {
    data class PlaceRefView(
        val placeId: Long,
        val name: String,
        val roadAddress: String,
    )

    data class GroupRefView(
        val groupId: Long,
        val name: String,
    )
}

data class TicketHistorySlice(
    val availableCount: Int,
    val items: List<TicketHistoryItemView>,
    val hasNext: Boolean,
)

interface GetTicketHistoryUseCase {
    fun list(
        userId: Long,
        after: TicketHistoryKey?,
        limit: Int,
    ): TicketHistorySlice
}
