package com.tmt.application.domain.user

import com.tmt.application.domain.media.MediaUrlResolver
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
import com.tmt.application.port.input.JoinedGroupView
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
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.roundToInt

/**
 * 마이페이지·타인 프로필 (TMT-274) — UserMockController를 대체하는 실구현.
 * 본인·타인이 같은 조회를 쓰고, 소유자 전용 필드(email·티켓·saveId) 가공은 컨트롤러 몫이다.
 */
@Service
class UserPageService(
    private val userPageQueryPort: UserPageQueryPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
    private val mediaUrlResolver: MediaUrlResolver,
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
        return ReviewGridSlice(
            items =
                rows.take(limit).map { row ->
                    ReviewGridItemView(
                        reviewId = row.reviewId,
                        saveId = row.saveId,
                        // 사진 0장 리뷰(C4-1)도 리뷰다 — 건너뛰면 칩의 reviewCount와 그리드 개수가 어긋난다.
                        // 썸네일만 비워 내리고 화면이 빈 썸네일을 그린다 (J §8-3)
                        thumbnailUrl = row.thumbnailS3Key?.let(mediaUrlResolver::urlOf),
                        placeId = row.placeId,
                        placeName = row.placeName,
                        placeCategoryName = FoodCategories.labelOf(row.placeCategoryId),
                        createdAt = row.createdAt,
                    )
                },
            hasNext = rows.size > limit,
        )
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
                    JoinedGroupView(
                        card =
                            GroupCardView(
                                groupId = row.groupId,
                                name = row.name,
                                oneLineDescription = row.oneLineDescription,
                                coverImageUrl = row.coverS3Key?.let(mediaUrlResolver::urlOf),
                                memberCount = row.memberCount,
                                reviewCount = row.reviewCount,
                                placeCount = row.placeCount,
                                matchedSavedPlaceCount = row.matchedSavedPlaceCount,
                            ),
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
                        categoryId = row.categoryId,
                        categoryName = FoodCategories.labelOf(row.categoryId),
                        averageRating = averageRating(row.ratingSum, row.reviewCount),
                        reviewCount = row.reviewCount,
                        thumbnailUrl = row.thumbnailS3Key?.let(mediaUrlResolver::urlOf),
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
        // 이력은 티켓이 실제로 오간 행만이다 (T10). 미완성 저장은 목록에 섞지 않고 건수 하나로 내린다 —
        // 화면이 `작성 중` 행 대신 상단 배너를 그리기 때문이다 (J §4-1, 2026-09-03 개정)
        val sorted =
            userPageQueryPort
                .findTicketLedgerRows(userId)
                .map { it.toItem() }
                .sortedWith(compareByDescending { orderOf(it.occurredAt, it.entryId) })
        val fromCursor =
            if (after == null) {
                sorted
            } else {
                val last = orderOf(after.occurredAt, after.entryId)
                sorted.filter { orderOf(it.occurredAt, it.entryId) < last }
            }
        return TicketHistorySlice(
            availableCount = groupJoinTicketPort.countAvailable(userId),
            inProgressSaveCount = userPageQueryPort.countInProgressSaves(userId),
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

    /** 출처가 다른 행이 한 목록에 섞이므로 접두로 네임스페이스를 가른다 — g=발급, c=소비, v=회수 */
    private fun TicketLedgerRow.entryId(): String =
        when (kind) {
            TicketLedgerKind.SIGNUP_GRANT, TicketLedgerKind.REVIEW_GRANT -> "${ENTRY_PREFIX}g$refId"
            TicketLedgerKind.GROUP_JOIN_CONSUME -> "${ENTRY_PREFIX}c$refId"
            TicketLedgerKind.REVIEW_DELETE_REVOKE -> "${ENTRY_PREFIX}v$refId"
        }

    /**
     * 이력 한 행의 정렬 위치. **정렬과 커서 자르기가 반드시 같은 기준을 써야** 페이지가 겹치거나 빠지지 않아서
     * 한 곳에서만 만든다.
     *
     * `entryId`를 문자열로 비교하면 `tkh_g9`가 `tkh_g10`보다 커서, 같은 시각에 발급된 두 행이
     * **번호 역순으로 뒤집혀 보인다.** 접두는 문자열로, 뒤의 일련번호는 숫자로 나눠서 본다 (PR #96 리뷰).
     */
    private fun orderOf(
        occurredAt: Instant,
        entryId: String,
    ): EntryOrder {
        val prefix = entryId.takeWhile { !it.isDigit() }
        return EntryOrder(occurredAt, prefix, entryId.removePrefix(prefix).toLongOrNull() ?: 0L)
    }

    /** 출처(prefix)가 다르면 그것으로, 같으면 일련번호로 가른다. 시각이 1차 키다. */
    private data class EntryOrder(
        val occurredAt: Instant,
        val prefix: String,
        val sequence: Long,
    ) : Comparable<EntryOrder> {
        override fun compareTo(other: EntryOrder): Int =
            compareValuesBy(this, other, { it.occurredAt }, { it.prefix }, { it.sequence })
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

    companion object {
        private const val ENTRY_PREFIX = "tkh_"
    }
}
