package com.tmt.application.domain.user

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.ReviewGridKey
import com.tmt.application.port.input.TicketHistoryItemType
import com.tmt.application.port.input.TicketHistoryKey
import com.tmt.application.port.output.persistence.FavoritePlaceRow
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.JoinedGroupRow
import com.tmt.application.port.output.persistence.ProfileHeaderRow
import com.tmt.application.port.output.persistence.ReviewGridRow
import com.tmt.application.port.output.persistence.TicketLedgerKind
import com.tmt.application.port.output.persistence.TicketLedgerRow
import com.tmt.application.port.output.persistence.UserPageQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPageServiceTest {
    private val queryPort = FakeUserPageQueryPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val service = UserPageService(queryPort, ticketPort, MediaUrlResolver("https://media.example.com/"))

    @Test
    fun `내 프로필에는 티켓 수가 실리고 타인 프로필에는 실리지 않는다`() {
        queryPort.header =
            ProfileHeaderRow(7L, "준형이", null, reviewCount = 3, joinedGroupCount = 2, favoritePlaceCount = 5)
        ticketPort.available = 4

        assertEquals(4, service.getMine(7L).availableTicketCount)
        assertNull(service.getOther(7L).availableTicketCount)
    }

    @Test
    fun `이메일은 수집하지 않으므로 항상 null이다`() {
        queryPort.header = ProfileHeaderRow(7L, "준형이", null, 0, 0, 0)

        assertNull(service.getMine(7L).email)
    }

    @Test
    fun `없는 사용자의 프로필은 USER_NOT_FOUND다`() {
        queryPort.header = null

        val e = assertFailsWith<TmtException> { service.getOther(404L) }

        assertEquals(ErrorCode.USER_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `없는 사용자의 탭 목록도 USER_NOT_FOUND다`() {
        queryPort.existing = emptySet()

        val e = assertFailsWith<TmtException> { service.list(404L, NO_REVIEW_CURSOR, limit = 20) }

        assertEquals(ErrorCode.USER_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `리뷰 그리드는 base-url로 썸네일 URL을 만들고 카테고리 라벨을 붙인다`() {
        queryPort.reviewRows =
            listOf(
                ReviewGridRow(1L, 11L, Instant.parse("2026-08-01T00:00:00Z"), "media/1.jpg", 5L, "김밥천국", "cat_korean"),
            )

        val item = service.list(7L, NO_REVIEW_CURSOR, limit = 20).items.single()

        assertEquals("https://media.example.com/media/1.jpg", item.thumbnailUrl)
        assertEquals("한식", item.placeCategoryName)
    }

    @Test
    fun `사진 0장 리뷰도 그리드에 남고 썸네일만 null이다`() {
        queryPort.reviewRows =
            listOf(
                ReviewGridRow(1L, 11L, Instant.parse("2026-08-02T00:00:00Z"), null, 5L, "김밥천국", null),
                ReviewGridRow(2L, 12L, Instant.parse("2026-08-01T00:00:00Z"), "media/2.jpg", 5L, "김밥천국", null),
            )

        val slice = service.list(7L, NO_REVIEW_CURSOR, limit = 20)

        // 건너뛰면 칩의 reviewCount와 그리드 개수가 어긋난다 (C4-1, J §8-3)
        assertEquals(listOf(1L, 2L), slice.items.map { it.reviewId })
        assertNull(slice.items[0].thumbnailUrl)
        assertEquals("https://media.example.com/media/2.jpg", slice.items[1].thumbnailUrl)
    }

    @Test
    fun `limit보다 한 행 더 오면 hasNext다`() {
        queryPort.reviewRows =
            (1..3L).map {
                ReviewGridRow(it, it + 10, Instant.parse("2026-08-01T00:00:00Z"), "m.jpg", 5L, "김밥천국", null)
            }

        val slice = service.list(7L, NO_REVIEW_CURSOR, limit = 2)

        assertTrue(slice.hasNext)
        assertEquals(2, slice.items.size)
    }

    @Test
    fun `좋아요 탭은 평점 평균을 소수 첫째 자리로 내리고 리뷰가 없으면 null이다`() {
        queryPort.favoriteRows =
            listOf(
                favoriteRow(placeId = 1L, ratingSum = 14, reviewCount = 3),
                favoriteRow(placeId = 2L, ratingSum = 0, reviewCount = 0),
            )

        val items = service.list(7L, viewerId = 7L, latitude = null, longitude = null, after = null, limit = 20).items

        assertEquals(4.7, items[0].averageRating)
        assertNull(items[1].averageRating)
    }

    @Test
    fun `그룹 탭은 커버 사진이 없으면 null로 내린다`() {
        queryPort.groupRows =
            listOf(
                JoinedGroupRow(3L, "매콤단짝", "맵부심 모임", null, 4, 10, 6, 2, Instant.parse("2026-07-01T00:00:00Z")),
            )

        val item = service.list(7L, viewerId = null, after = null, limit = 20).items.single()

        assertNull(item.card.coverImageUrl)
        assertEquals(2, item.card.matchedSavedPlaceCount)
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), item.joinedAt)
    }

    @Test
    fun `티켓 이력은 발급·소비·회수만 시각 역순으로 내리고 작성 중 저장은 건수로만 내린다`() {
        queryPort.ledgerRows =
            listOf(
                ledgerRow(TicketLedgerKind.SIGNUP_GRANT, refId = 1L, at = "2026-08-01T00:00:00Z"),
                ledgerRow(TicketLedgerKind.REVIEW_DELETE_REVOKE, refId = 4L, at = "2026-08-04T00:00:00Z"),
                ledgerRow(
                    TicketLedgerKind.GROUP_JOIN_CONSUME,
                    refId = 9L,
                    at = "2026-08-03T00:00:00Z",
                    groupId = 3L,
                    groupName = "매콤단짝",
                ),
            )
        queryPort.inProgressSaveCount = 2
        ticketPort.available = 4

        val slice = service.list(7L, NO_TICKET_CURSOR, limit = 20)

        // T10 (2026-09-03 개정) — 미완성 저장 행이 목록에 섞이지 않는다
        assertEquals(listOf("tkh_v4", "tkh_c9", "tkh_g1"), slice.items.map { it.entryId })
        assertEquals(listOf(-1, -1, 1), slice.items.map { it.amount })
        assertEquals(
            listOf(
                TicketHistoryItemType.REVIEW_DELETE_REVOKE,
                TicketHistoryItemType.GROUP_JOIN,
                TicketHistoryItemType.SIGNUP_REWARD,
            ),
            slice.items.map { it.type },
        )
        assertEquals("매콤단짝", slice.items[1].group?.name)
        assertNull(slice.items[2].place)
        assertEquals(2, slice.inProgressSaveCount)
        assertEquals(4, slice.availableCount)
    }

    @Test
    fun `같은 시각의 이력은 번호 순서대로 내려간다`() {
        // entryId를 문자열로 비교하면 tkh_g9 > tkh_g10이라 9가 10보다 위로 올라온다 (PR #96 리뷰)
        queryPort.ledgerRows =
            (9L..11L).map { ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = it, at = SAME_INSTANT) }

        val slice = service.list(7L, NO_TICKET_CURSOR, limit = 20)

        assertEquals(listOf("tkh_g11", "tkh_g10", "tkh_g9"), slice.items.map { it.entryId })
    }

    @Test
    fun `같은 시각의 커서도 번호 기준으로 잘린다`() {
        queryPort.ledgerRows =
            (9L..11L).map { ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = it, at = SAME_INSTANT) }

        // 문자열 비교였다면 tkh_g9 다음이 없어 빈 페이지가 나온다
        val second = service.list(7L, after = TicketHistoryKey(Instant.parse(SAME_INSTANT), "tkh_g10"), limit = 20)

        assertEquals(listOf("tkh_g9"), second.items.map { it.entryId })
    }

    @Test
    fun `작성 중 건수는 커서와 무관하게 전체 값이다`() {
        queryPort.ledgerRows =
            listOf(
                ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = 2L, at = "2026-08-02T00:00:00Z"),
                ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = 3L, at = "2026-08-03T00:00:00Z"),
            )
        queryPort.inProgressSaveCount = 1

        val secondPage =
            service.list(7L, after = TicketHistoryKey(Instant.parse("2026-08-03T00:00:00Z"), "tkh_g3"), limit = 20)

        assertEquals(listOf("tkh_g2"), secondPage.items.map { it.entryId })
        assertEquals(1, secondPage.inProgressSaveCount)
    }

    @Test
    fun `티켓 이력 커서는 마지막으로 본 행 다음부터 자른다`() {
        queryPort.ledgerRows =
            listOf(
                ledgerRow(TicketLedgerKind.SIGNUP_GRANT, refId = 1L, at = "2026-08-01T00:00:00Z"),
                ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = 2L, at = "2026-08-02T00:00:00Z"),
                ledgerRow(TicketLedgerKind.REVIEW_GRANT, refId = 3L, at = "2026-08-03T00:00:00Z"),
            )

        val slice =
            service.list(7L, after = TicketHistoryKey(Instant.parse("2026-08-03T00:00:00Z"), "tkh_g3"), limit = 20)

        assertEquals(listOf("tkh_g2", "tkh_g1"), slice.items.map { it.entryId })
        assertFalse(slice.hasNext)
    }

    companion object {
        private val NO_REVIEW_CURSOR: ReviewGridKey? = null
        private val NO_TICKET_CURSOR: TicketHistoryKey? = null

        /** 발급이 한 트랜잭션에서 여러 건 나가면 created_at이 같다 — tie-break가 드러나는 자리다. */
        private const val SAME_INSTANT = "2026-08-05T00:00:00Z"
    }

    private fun favoriteRow(
        placeId: Long,
        ratingSum: Long,
        reviewCount: Int,
    ) = FavoritePlaceRow(
        placeId = placeId,
        name = "김밥천국",
        roadAddress = "서울 마포구 오목로 1",
        regionName = "마포구 도화동",
        categoryId = null,
        reviewCount = reviewCount,
        ratingSum = ratingSum,
        thumbnailS3Key = null,
        distanceMeters = null,
        favoriteByViewer = true,
        favoritedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private fun ledgerRow(
        kind: TicketLedgerKind,
        refId: Long,
        at: String,
        groupId: Long? = null,
        groupName: String? = null,
    ) = TicketLedgerRow(
        kind = kind,
        refId = refId,
        occurredAt = Instant.parse(at),
        saveId = null,
        placeId = null,
        placeName = null,
        placeRoadAddress = null,
        groupId = groupId,
        groupName = groupName,
    )

    private class FakeUserPageQueryPort : UserPageQueryPort {
        var existing: Set<Long>? = null
        var header: ProfileHeaderRow? = null
        var reviewRows: List<ReviewGridRow> = emptyList()
        var groupRows: List<JoinedGroupRow> = emptyList()
        var favoriteRows: List<FavoritePlaceRow> = emptyList()
        var ledgerRows: List<TicketLedgerRow> = emptyList()
        var inProgressSaveCount = 0

        override fun userExists(userId: Long): Boolean = existing?.contains(userId) ?: true

        override fun findProfileHeader(userId: Long): ProfileHeaderRow? = header

        override fun findReviewGridRows(
            userId: Long,
            afterCreatedAt: Instant?,
            afterReviewId: Long?,
            limitPlusOne: Int,
        ): List<ReviewGridRow> = reviewRows.take(limitPlusOne)

        override fun findJoinedGroupRows(
            ownerId: Long,
            viewerId: Long?,
            afterJoinedAt: Instant?,
            afterGroupId: Long?,
            limitPlusOne: Int,
        ): List<JoinedGroupRow> = groupRows.take(limitPlusOne)

        override fun findFavoritePlaceRows(
            ownerId: Long,
            viewerId: Long?,
            latitude: Double?,
            longitude: Double?,
            afterFavoritedAt: Instant?,
            afterPlaceId: Long?,
            limitPlusOne: Int,
        ): List<FavoritePlaceRow> = favoriteRows.take(limitPlusOne)

        override fun findTicketLedgerRows(userId: Long): List<TicketLedgerRow> = ledgerRows

        override fun countInProgressSaves(userId: Long): Int = inProgressSaveCount
    }

    private class FakeGroupJoinTicketPort : GroupJoinTicketPort {
        var available = 0

        override fun countAvailable(userId: Long): Int = available

        override fun grantForReview(
            userId: Long,
            reviewId: Long,
        ) = error("이 테스트에서 쓰지 않는다")

        override fun revokeOneForReview(
            userId: Long,
            reviewId: Long,
        ): Boolean = error("이 테스트에서 쓰지 않는다")
    }
}
