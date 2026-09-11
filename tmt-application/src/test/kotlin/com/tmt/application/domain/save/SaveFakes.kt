package com.tmt.application.domain.save

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.GroupReviewQueryPort
import com.tmt.application.port.output.persistence.GroupReviewSharePort
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.application.port.output.persistence.MySaveRow
import com.tmt.application.port.output.persistence.MySaveRows
import com.tmt.application.port.output.persistence.NewPlaceRow
import com.tmt.application.port.output.persistence.PlaceCommandPort
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

    /** 실제 UPDATE와 같게 갱신 행 수를 돌려준다 — 없는 저장이면 0이다. */
    override fun updateSave(
        saveId: Long,
        rating: Int?,
        content: String?,
    ): Int {
        val index = saves.indexOfFirst { it.id == saveId }
        if (index < 0) return 0
        saves[index] = saves[index].copy(rating = rating, content = content)
        updatedAt[saveId] = Instant.now()
        return 1
    }

    override fun deletePhotos(saveId: Long) {
        photos -= saveId
    }

    override fun deleteTags(saveId: Long) {
        tags -= saveId
    }

    override fun deleteSave(saveId: Long): Int {
        if (!deleted.add(saveId)) return 0
        saves.removeIf { it.id == saveId }
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

    /** 가입 발급(T2)을 받은 사용자 — 몇 번 불렸는지까지 봐야 중복 발급을 잡는다 */
    val signupGrants = mutableListOf<Long>()

    override fun grantForSignup(userId: Long) {
        signupGrants += userId
        counts[userId] = counts.getOrDefault(userId, 0) + 1
    }

    override fun grantForReview(
        userId: Long,
        reviewId: Long,
    ) {
        counts[userId] = counts.getOrDefault(userId, 0) + 1
    }

    /** SKIP LOCKED가 남의 장을 건너뛴 상황 — 잔고가 있어도 회수가 실패한다 (TMT-351) */
    var revokeFails = false

    /** 회수는 잔고가 있을 때만 성공한다 — 0장이면 삭제가 거절되는 자리다 (R7). */
    override fun revokeOneForReview(
        userId: Long,
        reviewId: Long,
    ): Boolean {
        if (revokeFails) return false
        val available = counts.getOrDefault(userId, 0)
        if (available <= 0) return false
        counts[userId] = available - 1
        return true
    }

    val consumedFor = mutableListOf<Pair<Long, Long>>()

    /** 경합에 밀린 상황을 흉내낼 때 쓴다 — 잔고는 있는데 집을 수 있는 장이 0인 경우다 */
    var consumable: Int? = null

    override fun countConsumable(userId: Long): Int = consumable ?: countAvailable(userId)

    /** SKIP LOCKED가 남의 장을 건너뛴 상황 — 잔고가 있어도 소비가 실패한다 */
    var consumeFails = false

    override fun consumeOne(
        userId: Long,
        groupId: Long,
    ): Boolean {
        if (consumeFails) return false
        val available = counts.getOrDefault(userId, 0)
        if (available <= 0) return false
        counts[userId] = available - 1
        consumedFor += userId to groupId
        return true
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

/**
 * 그룹 공유 3종 페이크 (TMT-423). 저장 흐름은 멤버 여부만 묻고 공유·집계를 시킨다 —
 * 조회 쪽 나머지 메서드는 이 경로에서 쓰이지 않아 호출되면 실패시킨다.
 */
class FakeGroupSharePorts(
    private val memberships: MutableSet<Pair<Long, Long>> = mutableSetOf(),
) : GroupReviewSharePort,
    GroupStatsPort {
    val shared = mutableListOf<Triple<Long, Long, Long>>()
    val refreshedGroupIds = mutableListOf<Long>()

    fun addMembership(
        groupId: Long,
        userId: Long,
    ) {
        memberships += groupId to userId
    }

    fun isMemberOf(
        groupId: Long,
        userId: Long,
    ) = (groupId to userId) in memberships

    override fun share(
        groupId: Long,
        userId: Long,
        reviewId: Long,
    ) {
        // 실제 포트는 (groupId, reviewId) UNIQUE라 재호출이 무해하다 — 그 성질을 그대로 흉내낸다
        if (shared.none { it.first == groupId && it.third == reviewId }) {
            shared += Triple(groupId, userId, reviewId)
        }
    }

    override fun unshareAllByUser(
        groupId: Long,
        userId: Long,
    ): Int = error("저장 흐름에서 쓰이지 않는다")

    override fun findSharedGroupIds(reviewId: Long): List<Long> = error("저장 흐름에서 쓰이지 않는다")

    override fun unshareByReview(reviewId: Long): Int = error("저장 흐름에서 쓰이지 않는다")

    override fun replaceUserShares(
        groupId: Long,
        userId: Long,
        reviewIds: List<Long>,
    ) = error("저장 흐름에서 쓰이지 않는다")

    override fun addMember(groupId: Long) = error("저장 흐름에서 쓰이지 않는다")

    override fun removeMember(groupId: Long) = error("저장 흐름에서 쓰이지 않는다")

    override fun refreshShareStats(groupId: Long) {
        refreshedGroupIds += groupId
    }
}

/** 멤버 판정만 쓰는 조회 페이크 — 나머지는 저장 흐름 밖이다. */
class FakeGroupMembershipQueryPort(
    private val shares: FakeGroupSharePorts,
) : GroupReviewQueryPort {
    override fun existsGroup(groupId: Long): Boolean = error("저장 흐름에서 쓰이지 않는다")

    override fun isMember(
        groupId: Long,
        userId: Long,
    ): Boolean = shares.isMemberOf(groupId, userId)

    override fun findSharedReviewRows(
        groupId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        viewerId: Long?,
        viewerLatitude: Double?,
        viewerLongitude: Double?,
        limit: Int,
    ): PlaceReviewRows = error("저장 흐름에서 쓰이지 않는다")
}
