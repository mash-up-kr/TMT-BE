package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewRating
import com.tmt.application.port.output.persistence.UserWithdrawalPort
import com.tmt.output.persistence.postgres.repository.UserWithdrawalRepository
import org.springframework.stereotype.Component

@Component
class UserWithdrawalAdapter(
    private val repository: UserWithdrawalRepository,
) : UserWithdrawalPort {
    override fun findOwnedGroupIds(userId: Long): List<Long> = repository.findOwnedGroupIds(userId)

    override fun findActiveMembershipGroupIds(userId: Long): List<Long> =
        repository.findActiveMembershipGroupIds(userId)

    override fun findReviewRatings(userId: Long): List<ReviewRating> =
        repository.findReviewRatings(userId).map { ReviewRating(it.getPlaceId(), it.getRating()) }

    override fun findGroupIdsAffectedByShares(userId: Long): List<Long> =
        repository.findGroupIdsAffectedByShares(userId)

    override fun deleteOwnedGroups(groupIds: List<Long>) {
        // 빈 목록에 IN ()을 보내면 문법 오류다
        if (groupIds.isEmpty()) return
        repository.deleteSharesByGroupIds(groupIds)
        repository.deleteGroupPlacesByGroupIds(groupIds)
        repository.deleteRegionTagsByGroupIds(groupIds)
        repository.deleteMembershipsByGroupIds(groupIds)
        repository.deleteGroups(groupIds)
    }

    override fun deleteUserData(userId: Long) {
        repository.deleteSharesOfUser(userId)
        repository.deleteReviewSummaries(userId)
        repository.deleteReviews(userId)

        repository.deleteSaveTags(userId)
        repository.deleteSavePhotos(userId)
        repository.deleteSaves(userId)

        repository.deleteTickets(userId)
        repository.deleteRewardGrants(userId)

        repository.deleteMembershipsOfUser(userId)
        repository.deleteFavorites(userId)
        repository.deleteIdempotencyKeys(userId)

        repository.clearProfileImage(userId)
        repository.deleteMediaAssets(userId)

        repository.deleteUser(userId)
    }
}
