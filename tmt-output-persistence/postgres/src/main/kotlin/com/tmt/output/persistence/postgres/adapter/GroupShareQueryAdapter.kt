package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupShareQueryPort
import com.tmt.application.port.output.persistence.ReviewShareRow
import com.tmt.application.port.output.persistence.ReviewShareRows
import com.tmt.output.persistence.postgres.repository.GroupShareQueryRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GroupShareQueryAdapter(
    private val repository: GroupShareQueryRepository,
) : GroupShareQueryPort {
    override fun findMyReviewsWithShared(
        groupId: Long,
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
    ): ReviewShareRows {
        val rows = repository.findMyReviewsWithShared(groupId, userId, afterCreatedAt, afterReviewId, limit + 1)
        return ReviewShareRows(
            rows =
                rows.take(limit).map {
                    ReviewShareRow(
                        reviewId = it.getReviewId(),
                        placeName = it.getPlaceName(),
                        thumbnailS3Key = it.getThumbnailS3Key(),
                        content = it.getContent(),
                        isShared = it.getShared(),
                        createdAt = it.getCreatedAt(),
                    )
                },
            hasNext = rows.size > limit,
        )
    }

    override fun countSharedByUser(
        groupId: Long,
        userId: Long,
    ): Int = repository.countSharedByUser(groupId, userId).toInt()

    override fun findNotMine(
        userId: Long,
        reviewIds: List<Long>,
    ): List<Long> = if (reviewIds.isEmpty()) emptyList() else repository.findNotMine(userId, reviewIds.toTypedArray())

    override fun findSharedReviewIds(
        groupId: Long,
        userId: Long,
    ): List<Long> = repository.findSharedReviewIds(groupId, userId)
}
