package com.tmt.application.domain.save

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.NewPlaceRow
import com.tmt.application.port.output.persistence.PlaceCommandPort
import com.tmt.application.port.output.persistence.PlaceDetailRow
import com.tmt.application.port.output.persistence.PlacePhotoRow
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.application.port.output.persistence.PlaceReviewRows
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.ReviewTagPort
import com.tmt.application.port.output.persistence.ReviewTagRow
import com.tmt.application.port.output.persistence.SaveCommandPort
import java.time.Instant

class FakeSaveCommandPort : SaveCommandPort {
    data class Row(
        val id: Long,
        val userId: Long,
        val placeId: Long,
        val rating: Int?,
        val content: String?,
    )

    val saves = mutableListOf<Row>()
    val photos = mutableMapOf<Long, List<Long>>()
    val tags = mutableMapOf<Long, List<String>>()
    val reviews = mutableListOf<Long>()

    private var nextSaveId = 1L
    private var nextReviewId = 100L

    override fun insertSave(
        userId: Long,
        placeId: Long,
        rating: Int?,
        content: String?,
    ): Long {
        val id = nextSaveId++
        saves += Row(id, userId, placeId, rating, content)
        return id
    }

    override fun insertPhotos(
        saveId: Long,
        assetIds: List<Long>,
    ) {
        if (assetIds.isNotEmpty()) photos[saveId] = assetIds
    }

    override fun insertTags(
        saveId: Long,
        tagIds: Collection<String>,
    ) {
        if (tagIds.isNotEmpty()) tags[saveId] = tagIds.toList()
    }

    override fun insertReview(
        saveId: Long,
        userId: Long,
        placeId: Long,
    ): Long {
        val id = nextReviewId++
        reviews += id
        return id
    }
}

class FakePlaceCommandPort : PlaceCommandPort {
    val inserted = mutableListOf<NewPlaceRow>()

    private var nextPlaceId = 900L

    override fun insertManualPlace(place: NewPlaceRow): Long {
        inserted += place
        return nextPlaceId++
    }
}

class FakePlaceQueryPort(
    private val existingPlaceIds: Set<Long>,
) : PlaceQueryPort {
    override fun findPlaceDetail(
        placeId: Long,
        viewerId: Long?,
    ): PlaceDetailRow? = null

    override fun existsPlace(placeId: Long): Boolean = placeId in existingPlaceIds

    override fun findRecentPlacePhotos(
        placeId: Long,
        limit: Int,
    ): List<PlacePhotoRow> = emptyList()

    override fun findPlaceReviewRows(
        placeId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
    ): PlaceReviewRows = PlaceReviewRows(emptyList(), hasNext = false)
}

/** V2 시드와 같은 12종. `deactivate`로 비활성 태그를 흉내낸다. */
class FakeReviewTagPort : ReviewTagPort {
    private val companion = mutableSetOf("tag_alone", "tag_couple", "tag_friend", "tag_colleague", "tag_family")
    private val positive =
        mutableSetOf("tag_tasty", "tag_kind", "tag_mood", "tag_value", "tag_clean", "tag_transit", "tag_spacious")

    fun deactivate(tagId: String) {
        companion -= tagId
        positive -= tagId
    }

    override fun findActiveTags(tagIds: Collection<String>): List<ReviewTagRow> =
        tagIds.mapNotNull {
            when (it) {
                in companion -> ReviewTagRow(it, companion = true)
                in positive -> ReviewTagRow(it, companion = false)
                else -> null
            }
        }
}

class FakeGroupJoinTicketPort : GroupJoinTicketPort {
    private val counts = mutableMapOf<Long, Int>()

    fun seed(
        userId: Long,
        count: Int,
    ) {
        counts[userId] = count
    }

    override fun countAvailable(userId: Long): Int = counts.getOrDefault(userId, 0)

    override fun grantForReview(
        userId: Long,
        reviewId: Long,
    ) {
        counts[userId] = counts.getOrDefault(userId, 0) + 1
    }
}

class FakePlaceStatsPort : PlaceStatsPort {
    val added = mutableListOf<Pair<Long, Int>>()
    val removed = mutableListOf<Pair<Long, Int>>()

    override fun addReview(
        placeId: Long,
        rating: Int,
    ) {
        added += placeId to rating
    }

    override fun removeReview(
        placeId: Long,
        rating: Int,
    ) {
        removed += placeId to rating
    }
}
