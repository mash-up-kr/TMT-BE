package com.tmt.application.domain.save

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.MySaveRow
import com.tmt.application.port.output.persistence.MySaveRows
import com.tmt.application.port.output.persistence.PlaceDetailRow
import com.tmt.application.port.output.persistence.PlacePhotoRow
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.application.port.output.persistence.PlaceReviewRows
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.ReviewTagDefinitionRow
import com.tmt.application.port.output.persistence.ReviewTagPort
import com.tmt.application.port.output.persistence.ReviewTagRow
import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.application.port.output.persistence.SavePhotoRow
import com.tmt.application.port.output.persistence.SaveQueryPort
import com.tmt.application.port.output.persistence.SaveRow
import com.tmt.application.port.output.persistence.SaveTagRow
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
    val reviewIdBySave = mutableMapOf<Long, Long>()
    val deleted = mutableSetOf<Long>()
    val updatedAt = mutableMapOf<Long, Instant>()

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
        reviewIdBySave[saveId] = id
        return id
    }

    override fun updateSave(
        saveId: Long,
        rating: Int?,
        content: String?,
    ) {
        val index = saves.indexOfFirst { it.id == saveId }
        if (index >= 0) saves[index] = saves[index].copy(rating = rating, content = content)
        updatedAt[saveId] = Instant.now()
    }

    override fun deletePhotos(saveId: Long) {
        photos -= saveId
    }

    override fun deleteTags(saveId: Long) {
        tags -= saveId
    }

    override fun softDeleteSave(saveId: Long): Int {
        if (!deleted.add(saveId)) return 0
        return 1
    }
}

/** 쓰기는 FakeSaveCommandPort가 정본이다 — 읽기는 그 상태를 그대로 비춘다. */
class FakeSaveQueryPort(
    private val commandPort: FakeSaveCommandPort,
) : SaveQueryPort {
    override fun findSave(saveId: Long): SaveRow? {
        if (saveId in commandPort.deleted) return null
        val row = commandPort.saves.firstOrNull { it.id == saveId } ?: return null
        return SaveRow(
            saveId = row.id,
            userId = row.userId,
            reviewId = commandPort.reviewIdBySave[saveId],
            rating = row.rating,
            content = row.content,
            createdAt = Instant.EPOCH,
            placeId = row.placeId,
            placeName = "한판승부",
            placeRoadAddress = "서울 마포구 도화동 1",
            placeCategoryId = "cat_korean",
            aiSummaryPros = null,
            aiSummaryCons = null,
        )
    }

    override fun findSavePhotos(saveId: Long): List<SavePhotoRow> =
        commandPort.photos[saveId].orEmpty().mapIndexed { index, assetId ->
            SavePhotoRow(savePhotoId = assetId, s3Key = "photos/$assetId.jpg", photoOrder = index)
        }

    override fun findSaveTags(saveId: Long): List<SaveTagRow> =
        commandPort.tags[saveId].orEmpty().map { SaveTagRow(it, "라벨") }

    override fun findPhotoAssetIds(saveId: Long): List<Long> = commandPort.photos[saveId].orEmpty()

    override fun findMySaveRows(
        userId: Long,
        afterUpdatedAt: Instant?,
        afterSaveId: Long?,
        limit: Int,
    ): MySaveRows {
        val ordered =
            commandPort.saves
                .filter { it.userId == userId && it.id !in commandPort.deleted }
                .filter { it.id !in commandPort.reviewIdBySave }
                .map { it to commandPort.updatedAt.getOrDefault(it.id, Instant.EPOCH.plusSeconds(it.id)) }
                .sortedWith(
                    compareByDescending<Pair<FakeSaveCommandPort.Row, Instant>> { it.second }
                        .thenByDescending { it.first.id },
                ).filter { (row, updatedAt) ->
                    afterUpdatedAt == null ||
                        updatedAt < afterUpdatedAt ||
                        (updatedAt == afterUpdatedAt && row.id < afterSaveId!!)
                }
        return MySaveRows(
            rows =
                ordered.take(limit).map { (row, updatedAt) ->
                    MySaveRow(
                        saveId = row.id,
                        placeId = row.placeId,
                        placeName = "한판승부",
                        placeRoadAddress = "서울 마포구 도화동 1",
                        thumbnailS3Key = commandPort.photos[row.id]?.firstOrNull()?.let { "photos/$it.jpg" },
                        updatedAt = updatedAt,
                    )
                },
            hasNext = ordered.size > limit,
        )
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

    override fun findAllActiveDefinitions(): List<ReviewTagDefinitionRow> =
        companion.map { ReviewTagDefinitionRow(it, "동행", companion = true) } +
            positive.map { ReviewTagDefinitionRow(it, "좋은 점", companion = false) }

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
