package com.tmt.application.domain.user

import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.FavoriteKey
import com.tmt.application.port.input.FavoritePlaceView
import com.tmt.application.port.input.FavoriteSlice
import com.tmt.application.port.input.GetTicketHistoryUseCase
import com.tmt.application.port.input.GetUserFavoritesUseCase
import com.tmt.application.port.input.GetUserGroupsUseCase
import com.tmt.application.port.input.GetUserProfileUseCase
import com.tmt.application.port.input.GetUserReviewGridUseCase
import com.tmt.application.port.input.GroupCardSlice
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.JoinedGroupKey
import com.tmt.application.port.input.ReviewGridItemView
import com.tmt.application.port.input.ReviewGridKey
import com.tmt.application.port.input.ReviewGridSlice
import com.tmt.application.port.input.TicketHistoryItemType
import com.tmt.application.port.input.TicketHistoryItemView
import com.tmt.application.port.input.TicketHistoryKey
import com.tmt.application.port.input.TicketHistorySlice
import com.tmt.application.port.input.UserProfileView
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.TicketLedgerKind
import com.tmt.application.port.output.persistence.TicketLedgerRow
import com.tmt.application.port.output.persistence.UserPageQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

/**
 * 마이페이지·타인 프로필 (TMT-274) — UserMockController를 대체하는 실구현.
 * 본인·타인이 같은 조회를 쓰고, 소유자 전용 필드(email·티켓·saveId) 가공은 컨트롤러 몫이다.
 */
@Service
class UserPageService(
    private val userPageQueryPort: UserPageQueryPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
    @param:Value("\${tmt.media.base-url:}") private val mediaBaseUrl: String,
) : GetUserProfileUseCase,
    GetUserReviewGridUseCase,
    GetUserGroupsUseCase,
    GetUserFavoritesUseCase,
    GetTicketHistoryUseCase {
    override fun getMine(userId: Long): UserProfileView =
        profileOf(userId).copy(availableTicketCount = groupJoinTicketPort.countAvailable(userId))

    override fun getOther(targetUserId: Long): UserProfileView = profileOf(targetUserId)

    private fun profileOf(userId: Long): UserProfileView {
        val header = userPageQueryPort.findProfileHeader(userId) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
        return UserProfileView(
            userId = header.userId,
            nickname = header.nickname,
            profileImageUrl = header.profileImageUrl,
            reviewCount = header.reviewCount,
            joinedGroupCount = header.joinedGroupCount,
            favoritePlaceCount = header.favoritePlaceCount,
            availableTicketCount = null,
            email = null,
        )
    }

    override fun list(
        targetUserId: Long,
        after: ReviewGridKey?,
        limit: Int,
    ): ReviewGridSlice {
        ensureExists(targetUserId)
        val rows =
            userPageQueryPort.findReviewGridRows(
                userId = targetUserId,
                afterCreatedAt = after?.createdAt,
                afterReviewId = after?.reviewId,
                limitPlusOne = limit + 1,
            )
        val items =
            rows.take(limit).mapNotNull { row ->
                // 완성 리뷰는 사진이 보장된다 (C4). 결측이면 데이터 결함 — 그리드에 그릴 수 없어 건너뛴다
                val thumbnail = row.thumbnailS3Key
                if (thumbnail == null) {
                    logger.error { "완성 리뷰에 사진이 없다 - reviewId=${row.reviewId}" }
                    return@mapNotNull null
                }
                ReviewGridItemView(
                    reviewId = row.reviewId,
                    saveId = row.saveId,
                    thumbnailUrl = mediaUrl(thumbnail),
                    placeId = row.placeId,
                    placeName = row.placeName,
                    placeCategoryName = FoodCategories.labelOf(row.placeCategoryId),
                    createdAt = row.createdAt,
                )
            }
        return ReviewGridSlice(items = items, hasNext = rows.size > limit)
    }

    override fun list(
        targetUserId: Long,
        viewerId: Long?,
        after: JoinedGroupKey?,
        limit: Int,
    ): GroupCardSlice {
        ensureExists(targetUserId)
        val rows =
            userPageQueryPort.findJoinedGroupRows(
                ownerId = targetUserId,
                viewerId = viewerId,
                afterJoinedAt = after?.joinedAt,
                afterGroupId = after?.groupId,
                limitPlusOne = limit + 1,
            )
        return GroupCardSlice(
            items =
                rows.take(limit).map { row ->
                    GroupCardView(
                        groupId = row.groupId,
                        name = row.name,
                        oneLineDescription = row.oneLineDescription,
                        coverImageUrl = row.coverS3Key?.let(::mediaUrl),
                        memberCount = row.memberCount,
                        reviewCount = row.reviewCount,
                        placeCount = row.placeCount,
                        matchedSavedPlaceCount = row.matchedSavedPlaceCount,
                        joinedAt = row.joinedAt,
                    )
                },
            hasNext = rows.size > limit,
        )
    }

    override fun list(
        targetUserId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        after: FavoriteKey?,
        limit: Int,
    ): FavoriteSlice {
        ensureExists(targetUserId)
        val rows =
            userPageQueryPort.findFavoritePlaceRows(
                ownerId = targetUserId,
                viewerId = viewerId,
                latitude = latitude,
                longitude = longitude,
                afterFavoritedAt = after?.favoritedAt,
                afterPlaceId = after?.placeId,
                limitPlusOne = limit + 1,
            )
        return FavoriteSlice(
            items =
                rows.take(limit).map { row ->
                    FavoritePlaceView(
                        placeId = row.placeId,
                        name = row.name,
                        roadAddress = row.roadAddress,
                        regionName = row.regionName,
                        categoryName = FoodCategories.labelOf(row.categoryId),
                        averageRating = averageRating(row.ratingSum, row.reviewCount),
                        reviewCount = row.reviewCount,
                        thumbnailUrl = row.thumbnailS3Key?.let(::mediaUrl),
                        distanceMeters = row.distanceMeters,
                        isFavorite = row.favoriteByViewer,
                        favoritedAt = row.favoritedAt,
                    )
                },
            hasNext = rows.size > limit,
        )
    }

    override fun list(
        userId: Long,
        after: TicketHistoryKey?,
        limit: Int,
    ): TicketHistorySlice {
        ensureExists(userId)
        // 아직 티켓이 나가지 않은 저장도 같은 목록에 섞인다 — 목록을 나누면 클라이언트가 두 커서를 병합해야 한다
        val merged =
            userPageQueryPort.findTicketLedgerRows(userId).map { it.toItem() } +
                userPageQueryPort.findInProgressSaveRows(userId).map { save ->
                    TicketHistoryItemView(
                        entryId = "${ENTRY_PREFIX}s${save.saveId}",
                        type = TicketHistoryItemType.SAVE_IN_PROGRESS,
                        amount = null,
                        saveId = save.saveId,
                        place = TicketHistoryItemView.PlaceRefView(save.placeId, save.placeName, save.placeRoadAddress),
                        group = null,
                        occurredAt = save.updatedAt,
                    )
                }
        val sorted =
            merged.sortedWith(
                compareByDescending<TicketHistoryItemView> { it.occurredAt }.thenByDescending { it.entryId },
            )
        val fromCursor =
            if (after == null) {
                sorted
            } else {
                sorted.filter {
                    it.occurredAt < after.occurredAt ||
                        (it.occurredAt == after.occurredAt && it.entryId < after.entryId)
                }
            }
        return TicketHistorySlice(
            availableCount = groupJoinTicketPort.countAvailable(userId),
            items = fromCursor.take(limit),
            hasNext = fromCursor.size > limit,
        )
    }

    private fun TicketLedgerRow.toItem(): TicketHistoryItemView =
        TicketHistoryItemView(
            entryId = entryId(),
            type =
                when (kind) {
                    TicketLedgerKind.SIGNUP_GRANT -> TicketHistoryItemType.SIGNUP_REWARD
                    TicketLedgerKind.REVIEW_GRANT -> TicketHistoryItemType.REVIEW_REWARD
                    TicketLedgerKind.GROUP_JOIN_CONSUME -> TicketHistoryItemType.GROUP_JOIN
                    TicketLedgerKind.REVIEW_DELETE_REVOKE -> TicketHistoryItemType.REVIEW_DELETE_REVOKE
                },
            amount =
                when (kind) {
                    TicketLedgerKind.SIGNUP_GRANT, TicketLedgerKind.REVIEW_GRANT -> 1
                    TicketLedgerKind.GROUP_JOIN_CONSUME, TicketLedgerKind.REVIEW_DELETE_REVOKE -> -1
                },
            saveId = saveId,
            place =
                if (placeId != null && placeName != null && placeRoadAddress != null) {
                    TicketHistoryItemView.PlaceRefView(placeId, placeName, placeRoadAddress)
                } else {
                    null
                },
            group =
                if (groupId != null && groupName != null) {
                    TicketHistoryItemView.GroupRefView(groupId, groupName)
                } else {
                    null
                },
            occurredAt = occurredAt,
        )

    /** 출처가 다른 행이 한 목록에 섞이므로 접두로 네임스페이스를 가른다 — g=발급, c=소비, v=회수, s=작성 중 */
    private fun TicketLedgerRow.entryId(): String =
        when (kind) {
            TicketLedgerKind.SIGNUP_GRANT, TicketLedgerKind.REVIEW_GRANT -> "${ENTRY_PREFIX}g$refId"
            TicketLedgerKind.GROUP_JOIN_CONSUME -> "${ENTRY_PREFIX}c$refId"
            TicketLedgerKind.REVIEW_DELETE_REVOKE -> "${ENTRY_PREFIX}v$refId"
        }

    private fun ensureExists(userId: Long) {
        if (!userPageQueryPort.userExists(userId)) throw TmtException(ErrorCode.USER_NOT_FOUND)
    }

    /** 평균 = rating_sum / review_count, 소수 첫째 자리 (P9·규약 §8-3). 리뷰가 없으면 null */
    private fun averageRating(
        ratingSum: Long,
        reviewCount: Int,
    ): Double? {
        if (reviewCount <= 0) return null
        return (ratingSum.toDouble() / reviewCount * 10).roundToInt() / 10.0
    }

    /** 공개 읽기 버킷 (TMT-201) — base-url + s3_key가 곧 조회 URL이다 */
    private fun mediaUrl(s3Key: String): String = "${mediaBaseUrl.trimEnd('/')}/$s3Key"

    companion object {
        private const val ENTRY_PREFIX = "tkh_"
    }
}
