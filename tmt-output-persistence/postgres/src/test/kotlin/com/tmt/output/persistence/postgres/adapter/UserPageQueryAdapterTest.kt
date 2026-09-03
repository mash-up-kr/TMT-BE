package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.TicketLedgerKind
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 마이페이지 네이티브 쿼리 6종을 실제 PostGIS에서 검증한다 (TMT-274).
 *
 * 키셋 술어·상관 서브쿼리·CAST 파라미터는 단위 테스트가 잡지 못한다 — 여기서 실제 SQL을 태운다.
 * 각 테스트가 자기 사용자·매장을 새로 만들고 그 id로만 단언한다 (support 규칙).
 */
@Import(UserPageQueryAdapter::class)
class UserPageQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: UserPageQueryAdapter

    @Test
    fun `프로필 상단은 살아있는 리뷰·ACTIVE 멤버십·찜만 센다`() {
        val owner = newUser("상단검증")
        val other = newUser("상단이웃")
        val p1 = newPlace("상단매장1")
        val p2 = newPlace("상단매장2")
        newReview(owner, p1, createdAt = "2026-08-01T00:00:00Z")
        newReview(owner, p2, createdAt = "2026-08-02T00:00:00Z", deleted = true)
        newSave(owner, p1) // 리뷰 없는 저장은 세지 않는다 (R8)
        val g1 = newGroup(owner, "상단그룹A")
        val g2 = newGroup(other, "상단그룹B")
        newMembership(g1, owner, joinedAt = "2026-07-01T00:00:00Z")
        newMembership(g2, owner, joinedAt = "2026-07-02T00:00:00Z", status = "LEFT")
        newFavorite(owner, p1, at = "2026-08-01T00:00:00Z")
        newFavorite(owner, p2, at = "2026-08-02T00:00:00Z")

        val header = assertNotNull(adapter.findProfileHeader(owner))

        assertEquals("상단검증", header.nickname)
        assertEquals(1, header.reviewCount)
        assertEquals(1, header.joinedGroupCount)
        assertEquals(2, header.favoritePlaceCount)
        assertTrue(adapter.userExists(owner))
        assertFalse(adapter.userExists(-1L))
        assertNull(adapter.findProfileHeader(-1L))
    }

    @Test
    fun `리뷰 그리드는 최신순 키셋으로 넘어가고 사진 없는 리뷰는 썸네일이 NULL이다`() {
        val owner = newUser("그리드")
        val place = newPlace("그리드매장", categoryId = "cat_korean")
        val (s1, r1) = newReview(owner, place, createdAt = "2026-08-01T00:00:00Z")
        val (_, r2) = newReview(owner, place, createdAt = "2026-08-02T00:00:00Z")
        val (_, r3) = newReview(owner, place, createdAt = "2026-08-03T00:00:00Z")
        newReview(owner, place, createdAt = "2026-08-04T00:00:00Z", deleted = true)
        attachPhoto(owner, s1, order = 1, s3Key = "test/grid/$s1-b.jpg")
        attachPhoto(owner, s1, order = 0, s3Key = "test/grid/$s1-a.jpg")

        val first = adapter.findReviewGridRows(owner, null, null, limitPlusOne = 3)
        assertEquals(listOf(r3, r2), first.take(2).map { it.reviewId })
        assertEquals(3, first.size) // limit+1 행이 와야 hasNext를 판단할 수 있다

        val second =
            adapter.findReviewGridRows(owner, first[1].createdAt, first[1].reviewId, limitPlusOne = 3)
        assertEquals(listOf(r1), second.map { it.reviewId })
        assertEquals("test/grid/$s1-a.jpg", second.single().thumbnailS3Key) // photo_order 최소
        assertEquals("cat_korean", second.single().placeCategoryId)
        assertNull(first[0].thumbnailS3Key) // 사진 0장 리뷰 (C4-1)
    }

    @Test
    fun `그룹 탭은 가입 오래된 순이고 일치 수는 조회자의 저장 기준이다`() {
        val owner = newUser("그룹탭")
        val viewer = newUser("그룹뷰어")
        val place = newPlace("그룹탭매장")
        val g1 = newGroup(owner, "그룹탭A")
        val g2 = newGroup(owner, "그룹탭B")
        val g3 = newGroup(owner, "그룹탭C")
        newMembership(g1, owner, joinedAt = "2026-07-02T00:00:00Z")
        newMembership(g2, owner, joinedAt = "2026-07-01T00:00:00Z")
        newMembership(g3, owner, joinedAt = "2026-07-03T00:00:00Z", status = "LEFT")
        val (s1, r1) = newReview(owner, place, createdAt = "2026-08-01T00:00:00Z")
        attachPhoto(owner, s1, order = 0, s3Key = "test/group/$s1-cover.jpg")
        share(g1, r1, owner)
        jdbcTemplate.update(
            "INSERT INTO group_place (group_id, place_id, shared_review_count) VALUES (?, ?, 1)",
            g1,
            place,
        )
        newSave(viewer, place) // 조회자가 같은 매장을 저장했다 → g1과 1곳 일치 (G12)

        val asViewer = adapter.findJoinedGroupRows(owner, viewer, null, null, limitPlusOne = 10)
        assertEquals(listOf(g2, g1), asViewer.map { it.groupId }) // LEFT는 빠진다
        assertEquals(listOf(0, 1), asViewer.map { it.matchedSavedPlaceCount })
        assertEquals("test/group/$s1-cover.jpg", asViewer[1].coverS3Key)
        assertNull(asViewer[0].coverS3Key)

        val anonymous = adapter.findJoinedGroupRows(owner, null, null, null, limitPlusOne = 10)
        assertEquals(listOf(0, 0), anonymous.map { it.matchedSavedPlaceCount })

        val afterFirst = adapter.findJoinedGroupRows(owner, null, asViewer[0].joinedAt, asViewer[0].groupId, 10)
        assertEquals(listOf(g1), afterFirst.map { it.groupId })
    }

    @Test
    fun `좋아요 탭은 찜한 최신순이고 거리·조회자 찜 여부는 파라미터에 따른다`() {
        val owner = newUser("찜탭")
        val viewer = newUser("찜뷰어")
        val near = newPlace("찜근처", lng = 126.9780, lat = 37.5665, reviewCount = 3, ratingSum = 14)
        val far = newPlace("찜멀리", lng = 129.0756, lat = 35.1796)
        newFavorite(owner, near, at = "2026-08-01T00:00:00Z")
        newFavorite(owner, far, at = "2026-08-02T00:00:00Z")
        newFavorite(viewer, near, at = "2026-08-03T00:00:00Z")

        val withCoords = adapter.findFavoritePlaceRows(owner, viewer, 37.5665, 126.9780, null, null, limitPlusOne = 10)
        assertEquals(listOf(far, near), withCoords.map { it.placeId })
        assertEquals(listOf(false, true), withCoords.map { it.favoriteByViewer })
        assertEquals(0, withCoords[1].distanceMeters)
        assertTrue(withCoords[0].distanceMeters!! > 300_000)
        assertEquals(3, withCoords[1].reviewCount)
        assertEquals(14L, withCoords[1].ratingSum)

        val noCoords = adapter.findFavoritePlaceRows(owner, null, null, null, null, null, limitPlusOne = 10)
        assertTrue(noCoords.all { it.distanceMeters == null })
        assertTrue(noCoords.none { it.favoriteByViewer })

        val afterFirst = adapter.findFavoritePlaceRows(owner, null, null, null, withCoords[0].favoritedAt, far, 10)
        assertEquals(listOf(near), afterFirst.map { it.placeId })
    }

    @Test
    fun `티켓 원장은 발급·소비·회수를 모으고 작성 중 저장은 건수로 센다`() {
        val owner = newUser("원장")
        val p1 = newPlace("원장매장1")
        val p2 = newPlace("원장매장2")
        val (s1, r1) = newReview(owner, p1, createdAt = "2026-08-01T00:00:00Z")
        val (_, r2) = newReview(owner, p2, createdAt = "2026-08-02T00:00:00Z")
        newSave(owner, p1) // 작성 중
        newSave(owner, p2, deleted = true) // 버린 저장은 세지 않는다
        val group = newGroup(owner, "원장그룹")
        val signup = grant(owner, "SIGNUP", owner, at = "2026-07-31T00:00:00Z")
        val g1 = grant(owner, "REVIEW", r1, at = "2026-08-01T00:00:00Z")
        val g2 = grant(owner, "REVIEW", r2, at = "2026-08-02T00:00:00Z")
        val consumed =
            ticket(owner, g1, status = "CONSUMED", consumedGroupId = group, consumedAt = "2026-08-03T00:00:00Z")
        val revoked = ticket(owner, g2, status = "REVOKED", revokedAt = "2026-08-04T00:00:00Z")
        ticket(owner, signup, status = "AVAILABLE")

        val rows = adapter.findTicketLedgerRows(owner).sortedBy { it.occurredAt }

        assertEquals(
            listOf(
                TicketLedgerKind.SIGNUP_GRANT,
                TicketLedgerKind.REVIEW_GRANT,
                TicketLedgerKind.REVIEW_GRANT,
                TicketLedgerKind.GROUP_JOIN_CONSUME,
                TicketLedgerKind.REVIEW_DELETE_REVOKE,
            ),
            rows.map { it.kind },
        )
        assertEquals(listOf(signup, g1, g2, consumed, revoked), rows.map { it.refId })
        assertNull(rows[0].placeId)
        assertEquals(s1, rows[1].saveId)
        assertEquals("원장매장1", rows[1].placeName)
        assertEquals(group, rows[3].groupId)
        assertTrue(rows[3].groupName!!.startsWith("원장그룹")) // 픽스처가 UNIQUE 회피용 접미를 붙인다
        assertNull(rows[4].groupId)

        assertEquals(1, adapter.countInProgressSaves(owner))
    }

    // ── 픽스처 ──────────────────────────────────────────────

    private fun newUser(nickname: String): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (kakao_id, nickname) VALUES (?, ?) RETURNING id",
            Long::class.java,
            ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE),
            nickname,
        )!!

    private fun newPlace(
        name: String,
        categoryId: String? = null,
        lng: Double = 126.9,
        lat: Double = 37.5,
        reviewCount: Int = 0,
        ratingSum: Long = 0,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO place (external_source, external_id, name, road_address, region_name, category_id, location, review_count, rating_sum)
            VALUES ('TEST', ?, ?, '서울 어딘가 1', '마포구 도화동', ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
            RETURNING id
            """,
            Long::class.java,
            "tmt274-${ThreadLocalRandom.current().nextLong()}",
            name,
            categoryId,
            lng,
            lat,
            reviewCount,
            ratingSum,
        )!!

    private fun newSave(
        userId: Long,
        placeId: Long,
        deleted: Boolean = false,
    ): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO save (user_id, place_id, rating, content, deleted_at) VALUES (?, ?, 5, '맛있다', ?) RETURNING id",
            Long::class.java,
            userId,
            placeId,
            if (deleted) Instant.parse("2026-08-10T00:00:00Z").toTimestamp() else null,
        )!!

    /** 저장 + 리뷰. (saveId, reviewId)를 돌려준다. */
    private fun newReview(
        userId: Long,
        placeId: Long,
        createdAt: String,
        deleted: Boolean = false,
    ): Pair<Long, Long> {
        val saveId = newSave(userId, placeId)
        val reviewId =
            jdbcTemplate.queryForObject(
                "INSERT INTO review (save_id, user_id, place_id, created_at, deleted_at) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long::class.java,
                saveId,
                userId,
                placeId,
                Instant.parse(createdAt).toTimestamp(),
                if (deleted) Instant.parse("2026-08-10T00:00:00Z").toTimestamp() else null,
            )!!
        return saveId to reviewId
    }

    private fun attachPhoto(
        ownerId: Long,
        saveId: Long,
        order: Int,
        s3Key: String,
    ) {
        val assetId =
            jdbcTemplate.queryForObject(
                "INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status) VALUES (?, ?, 'image/jpeg', 1000, 'ATTACHED') RETURNING id",
                Long::class.java,
                ownerId,
                s3Key,
            )!!
        jdbcTemplate.update(
            "INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (?, ?, ?)",
            saveId,
            assetId,
            order,
        )
    }

    private fun newGroup(
        ownerId: Long,
        name: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO groups (name, one_line_description, food_category_id, owner_id)
            VALUES (?, '한 줄 소개', 'cat_korean', ?) RETURNING id
            """,
            Long::class.java,
            "$name-${ThreadLocalRandom.current().nextInt(1_000_000)}",
            ownerId,
        )!!

    private fun newMembership(
        groupId: Long,
        userId: Long,
        joinedAt: String,
        status: String = "ACTIVE",
    ) {
        jdbcTemplate.update(
            "INSERT INTO group_membership (group_id, user_id, status, joined_at) VALUES (?, ?, ?, ?)",
            groupId,
            userId,
            status,
            Instant.parse(joinedAt).toTimestamp(),
        )
    }

    private fun share(
        groupId: Long,
        reviewId: Long,
        userId: Long,
    ) {
        jdbcTemplate.update(
            "INSERT INTO group_review_share (group_id, review_id, user_id) VALUES (?, ?, ?)",
            groupId,
            reviewId,
            userId,
        )
    }

    private fun newFavorite(
        userId: Long,
        placeId: Long,
        at: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO place_favorite (user_id, place_id, created_at) VALUES (?, ?, ?)",
            userId,
            placeId,
            Instant.parse(at).toTimestamp(),
        )
    }

    private fun grant(
        userId: Long,
        sourceType: String,
        sourceId: Long,
        at: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
            VALUES (?, 'GROUP_JOIN_TICKET', ?, ?, ?) RETURNING id
            """,
            Long::class.java,
            userId,
            sourceType,
            sourceId,
            Instant.parse(at).toTimestamp(),
        )!!

    private fun ticket(
        userId: Long,
        grantId: Long,
        status: String,
        consumedGroupId: Long? = null,
        consumedAt: String? = null,
        revokedAt: String? = null,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO group_join_ticket (user_id, reward_grant_id, status, consumed_group_id, consumed_at, revoked_at)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """,
            Long::class.java,
            userId,
            grantId,
            status,
            consumedGroupId,
            consumedAt?.let { Instant.parse(it).toTimestamp() },
            revokedAt?.let { Instant.parse(it).toTimestamp() },
        )!!

    private fun Instant.toTimestamp() = java.sql.Timestamp.from(this)
}
