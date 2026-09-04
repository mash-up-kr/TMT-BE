package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupCardsQuery
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 그룹 탐색 쿼리에서 TMT-305로 바뀐 두 가지를 실제 DB에서 확인한다.
 *
 * - 커버 자구를 [com.tmt.output.persistence.postgres.repository.GroupCoverSql] 상수로 뽑아
 *   `@Query` 안에 문자열 템플릿으로 끼웠다 — **컴파일은 통과하고 실행에서 깨질 수 있는** 변경이다
 * - `excludeJoinedBy`가 새로 생겼다. 홈 추천 캐러셀이 이 조건으로 그룹 탐색을 재사용한다
 *
 * 탐색 쿼리는 그룹을 좁히는 조건이 없으면 DB의 모든 그룹을 훑는다. 컨테이너에 앞선 실행의 데이터가
 * 남아 있으므로 **음식 카테고리로 좁히고, 단언은 이 테스트가 만든 groupId로만** 한다.
 */
@Import(GroupExploreAdapter::class)
class GroupExploreAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupExploreAdapter

    @Test
    fun `커버는 공유 리뷰의 최신 사진 1장이다`() {
        val owner = fixtures.newUser()
        val place = fixtures.newPlace(categoryId = CATEGORY)
        val group = fixtures.newGroup(owner, foodCategoryId = CATEGORY)

        val older = fixtures.newPublishedReview(place, owner, createdAt = at(1))
        val newer = fixtures.newPublishedReview(place, owner, createdAt = at(2))
        fixtures.attachPhoto(older.saveId, fixtures.newMediaAsset(owner))
        // 최신 리뷰 안에서는 photo_order가 작은 쪽이 커버다 — 넣는 순서와 무관해야 한다
        fixtures.attachPhoto(newer.saveId, fixtures.newMediaAsset(owner), photoOrder = 1)
        val cover = fixtures.newMediaAsset(owner)
        fixtures.attachPhoto(newer.saveId, cover, photoOrder = 0)
        fixtures.shareReview(group, older.reviewId, owner)
        fixtures.shareReview(group, newer.reviewId, owner)

        val row = cardsOf(viewerId = owner).single { it.groupId == group }

        assertEquals(s3KeyOf(cover), row.coverS3Key)
    }

    @Test
    fun `공유 리뷰가 없거나 사진이 없으면 커버가 없다`() {
        val owner = fixtures.newUser()
        val place = fixtures.newPlace(categoryId = CATEGORY)
        val empty = fixtures.newGroup(owner, foodCategoryId = CATEGORY)
        val photoless = fixtures.newGroup(owner, foodCategoryId = CATEGORY)
        // 사진 0장 리뷰(C4-1)만 공유된 그룹 — 커버가 될 사진이 없다
        fixtures.shareReview(photoless, fixtures.newPublishedReview(place, owner).reviewId, owner)

        val rows = cardsOf(viewerId = owner).associateBy { it.groupId }

        assertNull(rows.getValue(empty).coverS3Key)
        assertNull(rows.getValue(photoless).coverS3Key)
    }

    @Test
    fun `삭제된 리뷰는 커버가 되지 않는다`() {
        val owner = fixtures.newUser()
        val place = fixtures.newPlace(categoryId = CATEGORY)
        val group = fixtures.newGroup(owner, foodCategoryId = CATEGORY)

        val alive = fixtures.newPublishedReview(place, owner, createdAt = at(1))
        val deleted = fixtures.newPublishedReview(place, owner, createdAt = at(3), deletedAt = at(4))
        val cover = fixtures.newMediaAsset(owner)
        fixtures.attachPhoto(alive.saveId, cover)
        fixtures.attachPhoto(deleted.saveId, fixtures.newMediaAsset(owner))
        fixtures.shareReview(group, alive.reviewId, owner)
        fixtures.shareReview(group, deleted.reviewId, owner)

        val row = cardsOf(viewerId = owner).single { it.groupId == group }

        // 삭제 리뷰가 더 최신이지만 살아있는 리뷰의 사진이 나와야 한다 (R6)
        assertEquals(s3KeyOf(cover), row.coverS3Key)
    }

    @Test
    fun `excludeJoinedBy는 그 사용자가 가입한 그룹을 뺀다`() {
        val owner = fixtures.newUser()
        val viewer = fixtures.newUser()
        val joined = fixtures.newGroup(owner, foodCategoryId = CATEGORY)
        val notJoined = fixtures.newGroup(owner, foodCategoryId = CATEGORY)
        val left = fixtures.newGroup(owner, foodCategoryId = CATEGORY)
        fixtures.newMembership(joined, viewer)
        fixtures.newMembership(left, viewer, status = "LEFT")

        val mine = setOf(joined, notJoined, left)
        val included = cardsOf(viewerId = viewer).map { it.groupId }.filter { it in mine }
        val excluded = cardsOf(viewerId = viewer, excludeJoinedBy = viewer).map { it.groupId }.filter { it in mine }

        assertEquals(setOf(joined, notJoined, left), included.toSet()) // null이면 전체를 본다 (탐색 목록)
        // 탈퇴(LEFT)는 가입이 아니므로 남는다 — ACTIVE만 뺀다
        assertEquals(setOf(notJoined, left), excluded.toSet())
    }

    /** 이 테스트가 만든 그룹만 보도록 카테고리로 좁힌다. 정렬·커서는 여기 관심사가 아니라 넉넉히 준다. */
    private fun cardsOf(
        viewerId: Long,
        excludeJoinedBy: Long? = null,
    ) = adapter
        .findGroupCards(
            GroupCardsQuery(
                viewerId = viewerId,
                query = null,
                queryFoodCategoryIds = emptyList(),
                queryRegionTagIds = emptyList(),
                foodCategoryId = CATEGORY,
                regionTagIds = emptyList(),
                sort = "RECOMMENDED",
                excludeJoinedBy = excludeJoinedBy,
                after = null,
                limit = 500,
            ),
        ).rows

    @Test
    fun `검색어의 퍼센트는 와일드카드가 아니라 글자다 (TMT-296)`() {
        val owner = fixtures.newUser()
        val literal = newNamedGroup(owner, "100% 국내산만 쓰는 집")
        val other = newNamedGroup(owner, "무관한 모임")

        val rows =
            adapter
                .findGroupCards(
                    GroupCardsQuery(
                        viewerId = null,
                        query = "100%",
                        queryFoodCategoryIds = emptyList(),
                        queryRegionTagIds = emptyList(),
                        foodCategoryId = CATEGORY,
                        regionTagIds = emptyList(),
                        sort = "RECOMMENDED",
                        after = null,
                        limit = 500,
                    ),
                ).rows
                .map { it.groupId }

        // ESCAPE가 빠지면 `%`가 전 그룹을 잡아 무관한 모임까지 나온다
        assertEquals(listOf(literal), rows)
        assertTrue(other !in rows)
    }

    /** 이름을 정해야 하는 검색 테스트용 — 공용 픽스처는 이름을 자동 생성한다. */
    private fun newNamedGroup(
        ownerId: Long,
        name: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO groups (name, one_line_description, food_category_id, owner_id)
            VALUES (?, '한 줄 소개', ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            "$name ${System.nanoTime()}",
            CATEGORY,
            ownerId,
        )!!

    private fun s3KeyOf(mediaAssetId: Long): String? =
        jdbcTemplate.queryForObject("SELECT s3_key FROM media_asset WHERE id = ?", String::class.java, mediaAssetId)

    private fun at(day: Int): Instant = Instant.parse("2026-08-0${day}T00:00:00Z")

    companion object {
        /** 다른 통합 테스트가 기본으로 쓰는 cat_korean과 겹치지 않게 고른다. */
        private const val CATEGORY = "cat_buffet"
    }
}
