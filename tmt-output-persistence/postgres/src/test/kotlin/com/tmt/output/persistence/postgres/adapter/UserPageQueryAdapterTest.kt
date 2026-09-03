package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.TicketLedgerKind
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 마이페이지 네이티브 쿼리 6종을 실제 PostGIS에서 검증한다 (TMT-274).
 *
 * 키셋 술어·상관 서브쿼리·CAST 파라미터·거리 계산은 단위 테스트가 잡지 못한다 — 여기서 실제 SQL을 태운다.
 * 행은 [com.tmt.output.persistence.postgres.support.PersistenceFixtures]로 만들고 그 id로만 단언한다.
 */
@Import(UserPageQueryAdapter::class)
class UserPageQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: UserPageQueryAdapter

    @Test
    fun `프로필 상단은 살아있는 리뷰·ACTIVE 멤버십·찜만 센다`() {
        val owner = fixtures.newUser("상단검증")
        val p1 = fixtures.newPlace()
        val p2 = fixtures.newPlace()
        fixtures.newPublishedReview(placeId = p1, userId = owner)
        fixtures.newPublishedReview(placeId = p2, userId = owner, deletedAt = AT_DELETE)
        fixtures.newSave(owner, p1) // 리뷰가 안 된 저장은 세지 않는다 (R8)
        fixtures.newMembership(fixtures.newGroup(owner), owner)
        fixtures.newMembership(fixtures.newGroup(owner), owner, status = "LEFT")
        fixtures.addFavorite(owner, p1)
        fixtures.addFavorite(owner, p2)

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
        val owner = fixtures.newUser()
        val place = fixtures.newPlace(categoryId = "cat_korean")
        val r1 = fixtures.newPublishedReview(place, owner, createdAt = at(1))
        val r2 = fixtures.newPublishedReview(place, owner, createdAt = at(2))
        val r3 = fixtures.newPublishedReview(place, owner, createdAt = at(3))
        fixtures.newPublishedReview(place, owner, createdAt = at(4), deletedAt = AT_DELETE)
        // 대표 사진은 photo_order 최소다 — 넣는 순서와 무관해야 한다
        fixtures.attachPhoto(r1.saveId, fixtures.newMediaAsset(owner), photoOrder = 1)
        val cover = fixtures.newMediaAsset(owner)
        fixtures.attachPhoto(r1.saveId, cover, photoOrder = 0)

        val first = adapter.findReviewGridRows(owner, null, null, limitPlusOne = 3)
        assertEquals(listOf(r3.reviewId, r2.reviewId), first.take(2).map { it.reviewId })
        assertEquals(3, first.size) // limit+1 행이 와야 hasNext를 판단할 수 있다
        assertNull(first[0].thumbnailS3Key) // 사진 0장 리뷰 (C4-1)

        val second = adapter.findReviewGridRows(owner, first[1].createdAt, first[1].reviewId, limitPlusOne = 3)

        assertEquals(listOf(r1.reviewId), second.map { it.reviewId })
        assertEquals(s3KeyOf(cover), second.single().thumbnailS3Key)
        assertEquals(r1.saveId, second.single().saveId)
        assertEquals("cat_korean", second.single().placeCategoryId)
    }

    @Test
    fun `그룹 탭은 가입 오래된 순이고 일치 수는 조회자의 저장 기준이다`() {
        val owner = fixtures.newUser()
        val viewer = fixtures.newUser()
        val place = fixtures.newPlace()
        val g1 = fixtures.newGroup(owner)
        val g2 = fixtures.newGroup(owner)
        fixtures.newMembership(g1, owner, joinedAt = at(2))
        fixtures.newMembership(g2, owner, joinedAt = at(1))
        fixtures.newMembership(fixtures.newGroup(owner), owner, joinedAt = at(3), status = "LEFT")
        val shared = fixtures.newPublishedReview(place, owner)
        val cover = fixtures.newMediaAsset(owner)
        fixtures.attachPhoto(shared.saveId, cover)
        fixtures.shareReview(g1, shared.reviewId, owner)
        fixtures.addGroupPlace(g1, place)
        fixtures.newSave(viewer, place) // 조회자가 같은 매장을 저장했다 → g1과 1곳 일치 (G12)

        val asViewer = adapter.findJoinedGroupRows(owner, viewer, null, null, limitPlusOne = 10)

        assertEquals(listOf(g2, g1), asViewer.map { it.groupId }) // LEFT는 빠지고 가입 오래된 순이다
        assertEquals(listOf(0, 1), asViewer.map { it.matchedSavedPlaceCount })
        assertEquals(s3KeyOf(cover), asViewer[1].coverS3Key)
        assertNull(asViewer[0].coverS3Key)

        // 비로그인은 일치 수가 0이다 (§6-1)
        assertEquals(
            listOf(0, 0),
            adapter.findJoinedGroupRows(owner, null, null, null, 10).map { it.matchedSavedPlaceCount },
        )

        val afterFirst = adapter.findJoinedGroupRows(owner, null, asViewer[0].joinedAt, asViewer[0].groupId, 10)
        assertEquals(listOf(g1), afterFirst.map { it.groupId })
    }

    @Test
    fun `좋아요 탭은 찜한 최신순이고 거리·조회자 찜 여부는 파라미터에 따른다`() {
        val owner = fixtures.newUser()
        val viewer = fixtures.newUser()
        val near = fixtures.newPlace(latitude = SEOUL, longitude = SEOUL_LNG, reviewCount = 3, ratingSum = 14)
        val far = fixtures.newPlace(latitude = BUSAN, longitude = BUSAN_LNG)
        fixtures.addFavorite(owner, near, createdAt = at(1))
        fixtures.addFavorite(owner, far, createdAt = at(2))
        fixtures.addFavorite(viewer, near)

        val withCoords = adapter.findFavoritePlaceRows(owner, viewer, SEOUL, SEOUL_LNG, null, null, limitPlusOne = 10)

        assertEquals(listOf(far, near), withCoords.map { it.placeId }) // 찜한 최신순
        assertEquals(listOf(false, true), withCoords.map { it.favoriteByViewer }) // 조회자 기준 (F3)
        assertEquals(0, withCoords[1].distanceMeters)
        assertTrue(withCoords[0].distanceMeters!! > 300_000)
        assertEquals(3, withCoords[1].reviewCount)
        assertEquals(14L, withCoords[1].ratingSum)

        // 좌표가 없으면 거리를 계산하지 않고, 조회자가 없으면 찜 플래그가 전부 false다
        val anonymous = adapter.findFavoritePlaceRows(owner, null, null, null, null, null, limitPlusOne = 10)
        assertTrue(anonymous.all { it.distanceMeters == null })
        assertTrue(anonymous.none { it.favoriteByViewer })

        val afterFirst = adapter.findFavoritePlaceRows(owner, null, null, null, withCoords[0].favoritedAt, far, 10)
        assertEquals(listOf(near), afterFirst.map { it.placeId })
    }

    @Test
    fun `티켓 원장은 발급·소비·회수를 모으고 작성 중 저장은 건수로 센다`() {
        val owner = fixtures.newUser()
        val p1 = fixtures.newPlace()
        val p2 = fixtures.newPlace()
        val r1 = fixtures.newPublishedReview(p1, owner)
        val r2 = fixtures.newPublishedReview(p2, owner)
        fixtures.newSave(owner, p1) // 작성 중
        fixtures.newSave(owner, p2, deletedAt = AT_DELETE) // 버린 저장은 세지 않는다
        val group = fixtures.newGroup(owner)
        val signup = fixtures.grantReward(owner, "SIGNUP", owner, at(1))
        val g1 = fixtures.grantReward(owner, "REVIEW", r1.reviewId, at(2))
        val g2 = fixtures.grantReward(owner, "REVIEW", r2.reviewId, at(3))
        val consumed = fixtures.newTicket(owner, g1, "CONSUMED", consumedGroupId = group, consumedAt = at(4))
        val revoked = fixtures.newTicket(owner, g2, "REVOKED", revokedAt = at(5))
        fixtures.newTicket(owner, signup)

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
        assertNull(rows[0].placeId) // 가입 보상은 매장·그룹이 둘 다 없다 (T11)
        assertNull(rows[0].groupId)
        assertEquals(r1.saveId, rows[1].saveId)
        assertEquals(p1, rows[1].placeId)
        assertEquals(group, rows[3].groupId)
        assertNull(rows[4].groupId) // 회수 행은 원인 리뷰를 저장하지 않는다

        assertEquals(1, adapter.countInProgressSaves(owner))
    }

    private fun s3KeyOf(mediaAssetId: Long): String? =
        jdbcTemplate.queryForObject("SELECT s3_key FROM media_asset WHERE id = ?", String::class.java, mediaAssetId)

    /** 커서 경계를 보려면 시각이 결정적이어야 한다 — 테스트가 순서를 직접 준다. */
    private fun at(day: Int): Instant = Instant.parse("2026-08-0${day}T00:00:00Z")

    companion object {
        private val AT_DELETE = Instant.parse("2026-08-20T00:00:00Z")
        private const val SEOUL = 37.5665
        private const val SEOUL_LNG = 126.9780
        private const val BUSAN = 35.1796
        private const val BUSAN_LNG = 129.0756
    }
}
