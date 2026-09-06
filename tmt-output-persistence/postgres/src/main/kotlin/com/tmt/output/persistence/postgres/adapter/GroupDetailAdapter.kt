package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupCoverImageRow
import com.tmt.application.port.output.persistence.GroupDetailPort
import com.tmt.application.port.output.persistence.GroupDetailRow
import com.tmt.output.persistence.postgres.repository.GroupDetailRepository
import org.springframework.stereotype.Component

@Component
class GroupDetailAdapter(
    private val repository: GroupDetailRepository,
) : GroupDetailPort {
    override fun findDetail(
        groupId: Long,
        viewerId: Long?,
    ): GroupDetailRow? =
        repository.findDetail(groupId, viewerId)?.let {
            GroupDetailRow(
                groupId = it.getGroupId(),
                name = it.getName(),
                oneLineDescription = it.getOneLineDescription(),
                description = it.getDescription(),
                imageS3Key = it.getImageS3Key(),
                ownerId = it.getOwnerId(),
                memberCount = it.getMemberCount(),
                reviewCount = it.getReviewCount(),
                placeCount = it.getPlaceCount(),
                foodCategoryId = it.getFoodCategoryId(),
                matchedSavedPlaceCount = it.getMatchedSavedPlaceCount().toInt(),
                isMember = it.getIsMember(),
            )
        }

    override fun findRegionTagIds(groupId: Long): List<String> = repository.findRegionTagIds(groupId)

    override fun findCoverImages(
        groupId: Long,
        limit: Int,
    ): List<GroupCoverImageRow> =
        repository.findCoverImages(groupId, limit).map {
            GroupCoverImageRow(s3Key = it.getS3Key(), reviewId = it.getReviewId())
        }
}
