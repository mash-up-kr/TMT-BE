package com.tmt.application.domain.review

import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewCommandPort
import com.tmt.application.port.output.persistence.ReviewDeletionRow
import com.tmt.application.port.output.persistence.ReviewDetailRow
import com.tmt.application.port.output.persistence.ReviewQueryPort
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import java.time.Instant

val FIXED_CREATED_AT: Instant = Instant.parse("2026-08-12T09:11:03.412Z")

fun reviewDetailRow(
    reviewId: Long = 1,
    saveId: Long = 10,
    authorId: Long = 7,
) = ReviewDetailRow(
    reviewId = reviewId,
    saveId = saveId,
    createdAt = FIXED_CREATED_AT,
    rating = 5,
    content = "맛도 있고 분위기도 좋아요.",
    authorId = authorId,
    authorNickname = "하아얀",
    authorProfileImageUrl = null,
    placeId = 9,
    placeName = "델리스피자",
    placeRoadAddress = "서울 마포구 도화동 200-14",
    placeCategoryId = "cat_western",
)

class FakeReviewQueryPort(
    private val details: Map<Long, ReviewDetailRow> = emptyMap(),
    private val deletions: Map<Long, ReviewDeletionRow> = emptyMap(),
) : ReviewQueryPort {
    override fun findReviewDetail(reviewId: Long): ReviewDetailRow? = details[reviewId]

    override fun findReviewForDeletion(reviewId: Long): ReviewDeletionRow? = deletions[reviewId]
}

class FakeReviewCommandPort(
    private val assetIdsBySave: Map<Long, List<Long>> = emptyMap(),
) : ReviewCommandPort {
    val deletedPhotoSaveIds = mutableListOf<Long>()
    val softDeletedReviews = mutableListOf<Long>()
    val softDeletedSaves = mutableListOf<Long>()

    override fun deletePhotoLinks(saveId: Long): List<Long> {
        deletedPhotoSaveIds += saveId
        return assetIdsBySave[saveId].orEmpty()
    }

    /** 이미 지워진 행이면 0 — 조건부 UPDATE의 실제 동작과 같다 (D6) */
    override fun softDeleteReview(reviewId: Long): Int {
        if (reviewId in softDeletedReviews) return 0
        softDeletedReviews += reviewId
        return 1
    }

    override fun softDeleteSave(saveId: Long): Int {
        softDeletedSaves += saveId
        return 1
    }
}

class FakeGroupReviewSharePort(
    private val groupIdsByReview: Map<Long, List<Long>> = emptyMap(),
) : GroupReviewSharePort {
    val unsharedReviewIds = mutableListOf<Long>()

    override fun share(
        groupId: Long,
        userId: Long,
        reviewId: Long,
    ) = Unit

    override fun unshareAllByUser(
        groupId: Long,
        userId: Long,
    ): Int = 0

    override fun findSharedGroupIds(reviewId: Long): List<Long> = groupIdsByReview[reviewId].orEmpty()

    override fun unshareByReview(reviewId: Long): Int {
        unsharedReviewIds += reviewId
        return groupIdsByReview[reviewId].orEmpty().size
    }
}

class FakeGroupStatsPort : GroupStatsPort {
    val refreshed = mutableListOf<Long>()

    override fun addMember(groupId: Long) = Unit

    override fun removeMember(groupId: Long) = Unit

    override fun refreshShareStats(groupId: Long) {
        refreshed += groupId
    }
}

class FakeReviewCardLookupPort(
    private val photos: List<PhotoRow> = emptyList(),
    private val tags: List<TagRow> = emptyList(),
    private val summaries: List<SummaryRow> = emptyList(),
) : ReviewCardLookupPort {
    override fun findPhotoRows(saveIds: Collection<Long>): List<PhotoRow> = photos.filter { it.saveId in saveIds }

    override fun findTagRows(saveIds: Collection<Long>): List<TagRow> = tags.filter { it.saveId in saveIds }

    override fun findSummaryRows(reviewIds: Collection<Long>): List<SummaryRow> =
        summaries.filter { it.reviewId in reviewIds }
}
