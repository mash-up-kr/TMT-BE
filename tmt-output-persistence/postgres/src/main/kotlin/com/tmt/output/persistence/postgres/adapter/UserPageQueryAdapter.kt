package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.FavoritePlaceRow
import com.tmt.application.port.output.persistence.InProgressSaveRow
import com.tmt.application.port.output.persistence.JoinedGroupRow
import com.tmt.application.port.output.persistence.ProfileHeaderRow
import com.tmt.application.port.output.persistence.ReviewGridRow
import com.tmt.application.port.output.persistence.TicketLedgerKind
import com.tmt.application.port.output.persistence.TicketLedgerRow
import com.tmt.application.port.output.persistence.UserPageQueryPort
import com.tmt.output.persistence.postgres.repository.UserPageQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class UserPageQueryAdapter(
    private val userPageQueryRepository: UserPageQueryRepository,
) : UserPageQueryPort {
    override fun userExists(userId: Long): Boolean = userPageQueryRepository.existsById(userId)

    override fun findProfileHeader(userId: Long): ProfileHeaderRow? =
        userPageQueryRepository.findProfileHeader(userId)?.let {
            ProfileHeaderRow(
                userId = it.getUserId(),
                nickname = it.getNickname(),
                profileImageUrl = it.getProfileImageUrl(),
                reviewCount = it.getReviewCount(),
                joinedGroupCount = it.getJoinedGroupCount(),
                favoritePlaceCount = it.getFavoritePlaceCount(),
            )
        }

    override fun findReviewGridRows(
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limitPlusOne: Int,
    ): List<ReviewGridRow> =
        userPageQueryRepository
            .findReviewGridRows(userId, afterCreatedAt, afterReviewId, limitPlusOne)
            .map {
                ReviewGridRow(
                    reviewId = it.getReviewId(),
                    saveId = it.getSaveId(),
                    createdAt = it.getCreatedAt(),
                    thumbnailS3Key = it.getThumbnailS3Key(),
                    placeId = it.getPlaceId(),
                    placeName = it.getPlaceName(),
                    placeCategoryId = it.getPlaceCategoryId(),
                )
            }

    override fun findJoinedGroupRows(
        ownerId: Long,
        viewerId: Long?,
        afterJoinedAt: Instant?,
        afterGroupId: Long?,
        limitPlusOne: Int,
    ): List<JoinedGroupRow> =
        userPageQueryRepository
            .findJoinedGroupRows(ownerId, viewerId, afterJoinedAt, afterGroupId, limitPlusOne)
            .map {
                JoinedGroupRow(
                    groupId = it.getGroupId(),
                    name = it.getName(),
                    oneLineDescription = it.getOneLineDescription(),
                    coverS3Key = it.getCoverS3Key(),
                    memberCount = it.getMemberCount(),
                    reviewCount = it.getReviewCount(),
                    placeCount = it.getPlaceCount(),
                    matchedSavedPlaceCount = it.getMatchedSavedPlaceCount(),
                    joinedAt = it.getJoinedAt(),
                )
            }

    override fun findFavoritePlaceRows(
        ownerId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        afterFavoritedAt: Instant?,
        afterPlaceId: Long?,
        limitPlusOne: Int,
    ): List<FavoritePlaceRow> =
        userPageQueryRepository
            .findFavoritePlaceRows(ownerId, viewerId, latitude, longitude, afterFavoritedAt, afterPlaceId, limitPlusOne)
            .map {
                FavoritePlaceRow(
                    placeId = it.getPlaceId(),
                    name = it.getName(),
                    roadAddress = it.getRoadAddress(),
                    regionName = it.getRegionName(),
                    categoryId = it.getCategoryId(),
                    reviewCount = it.getReviewCount(),
                    ratingSum = it.getRatingSum(),
                    thumbnailS3Key = it.getThumbnailS3Key(),
                    distanceMeters = it.getDistanceMeters(),
                    favoriteByViewer = it.getFavoriteByViewer(),
                    favoritedAt = it.getFavoritedAt(),
                )
            }

    override fun findTicketLedgerRows(userId: Long): List<TicketLedgerRow> =
        userPageQueryRepository.findTicketLedgerRows(userId).map {
            TicketLedgerRow(
                kind = kindOf(it.getRowKind(), it.getSourceType()),
                refId = it.getRefId(),
                occurredAt = it.getOccurredAt(),
                saveId = it.getSaveId(),
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                placeRoadAddress = it.getPlaceRoadAddress(),
                groupId = it.getGroupId(),
                groupName = it.getGroupName(),
            )
        }

    override fun findInProgressSaveRows(userId: Long): List<InProgressSaveRow> =
        userPageQueryRepository.findInProgressSaveRows(userId).map {
            InProgressSaveRow(
                saveId = it.getSaveId(),
                updatedAt = it.getUpdatedAt(),
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                placeRoadAddress = it.getPlaceRoadAddress(),
            )
        }

    private fun kindOf(
        rowKind: String,
        sourceType: String?,
    ): TicketLedgerKind =
        when (rowKind) {
            "GRANT" -> if (sourceType == "SIGNUP") TicketLedgerKind.SIGNUP_GRANT else TicketLedgerKind.REVIEW_GRANT
            "CONSUME" -> TicketLedgerKind.GROUP_JOIN_CONSUME
            else -> TicketLedgerKind.REVIEW_DELETE_REVOKE
        }
}
