package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.input.UserRankingSort
import com.tmt.application.port.output.persistence.UserRankingRow
import com.tmt.application.port.output.persistence.UserRankingsQuery
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 유저 활동량 랭킹 네이티브 쿼리 (TMT-436).
 *
 * 랭킹은 전 유저가 대상이라 같은 컨테이너를 쓰는 앞선 테스트의 사용자가 섞인다 — 각 테스트는
 * 자기가 만든 id만 골라서 단언한다.
 */
@Import(UserRankingQueryAdapter::class)
class UserRankingQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: UserRankingQueryAdapter

    @Test
    fun `제외 목록에 든 계정은 랭킹에 나오지 않고 빈 목록이면 아무도 빠지지 않는다`() {
        // 팀원 테스트·시드 계정이 상위를 차지하면 리더보드가 의미를 잃는다 — 설정 id 목록으로 거른다
        val excluded = fixtures.newUser("제외대상")
        val kept = fixtures.newUser("남는사람")

        UserRankingSort.entries.forEach { sort ->
            val ids =
                adapter
                    .findUserRankings(
                        UserRankingsQuery(sort = sort, after = null, limit = 1000, excludedUserIds = listOf(excluded)),
                    ).rows
                    .map { it.userId }
            assertFalse(excluded in ids, "$sort: 제외 계정이 나왔다")
            assertTrue(kept in ids, "$sort: 제외 목록에 없는 계정이 빠졌다")
        }

        val all =
            adapter
                .findUserRankings(UserRankingsQuery(UserRankingSort.entries.first(), null, 1000))
                .rows
                .map { it.userId }
        assertTrue(excluded in all && kept in all, "빈 목록이면 둘 다 나온다")
    }

    @Test
    fun `리뷰 수는 살아있는 리뷰만 세고 공유가 없으면 0이다`() {
        val user = fixtures.newUser("랭킹없음")
        val place = fixtures.newPlace()
        fixtures.newPublishedReview(place, userId = user)
        fixtures.newPublishedReview(place, userId = user)
        fixtures.newPublishedReview(place, userId = user, deletedAt = java.time.Instant.now())

        val row = rowOf(user)

        assertEquals("랭킹없음", row.nickname)
        assertEquals(2, row.reviewCount)
        assertEquals(0, row.sharedReviewCount)
    }

    @Test
    fun `한 리뷰를 여러 그룹에 공유해도 공유 리뷰 수는 1이다`() {
        val user = fixtures.newUser("여러그룹공유")
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place, userId = user).reviewId
        val another = fixtures.newPublishedReview(place, userId = user).reviewId
        repeat(3) { fixtures.shareReview(fixtures.newGroup(user), review, user) }
        fixtures.shareReview(fixtures.newGroup(user), another, user)

        assertEquals(2, rowOf(user).sharedReviewCount)
    }

    @Test
    fun `삭제된 리뷰의 공유는 세지 않는다`() {
        val user = fixtures.newUser("공유후삭제")
        val place = fixtures.newPlace()
        val alive = fixtures.newPublishedReview(place, userId = user).reviewId
        val deleted = fixtures.newPublishedReview(place, userId = user, deletedAt = java.time.Instant.now()).reviewId
        val group = fixtures.newGroup(user)
        fixtures.shareReview(group, alive, user)
        fixtures.shareReview(group, deleted, user)

        assertEquals(1, rowOf(user).sharedReviewCount)
    }

    @Test
    fun `남이 공유한 리뷰는 내 공유 수에 들어가지 않는다`() {
        val mine = fixtures.newUser("내공유")
        val other = fixtures.newUser("남공유")
        val place = fixtures.newPlace()
        val group = fixtures.newGroup(mine)
        fixtures.shareReview(group, fixtures.newPublishedReview(place, userId = mine).reviewId, mine)
        fixtures.shareReview(group, fixtures.newPublishedReview(place, userId = other).reviewId, other)

        assertEquals(1, rowOf(mine).sharedReviewCount)
        assertEquals(1, rowOf(other).sharedReviewCount)
    }

    @Test
    fun `리뷰가 없는 사용자도 목록에 들어간다`() {
        val user = fixtures.newUser("리뷰0")

        assertEquals(0, rowOf(user).reviewCount)
    }

    @Test
    fun `가입을 끝내지 않은 사용자는 목록에서 빠진다`() {
        val place = fixtures.newPlace()
        val pending = fixtures.newUser("미완료", profileCompletedAt = null)
        fixtures.newPublishedReview(place, userId = pending)

        assertFalse(allUserIds().contains(pending))
    }

    @Test
    fun `프로필 사진 자산이 있으면 s3 key를 같이 돌려준다`() {
        val user = fixtures.newUser("사진유저")
        val s3Key = fixtures.attachProfileImage(user, legacyUrl = "https://kakao.example/old.png")

        val row = rowOf(user)

        assertEquals(s3Key, row.profileImageS3Key)
        assertEquals("https://kakao.example/old.png", row.profileImageUrl)
    }

    @Test
    fun `커서가 리뷰 수 동률 경계에서 중복도 누락도 없다`() {
        // 리뷰 수가 전부 같은 넷 — tie-breaker(user_id)만으로 갈린다
        val place = fixtures.newPlace()
        val mine =
            (1..4).map {
                val user = fixtures.newUser("동률$it")
                repeat(2) { fixtures.newPublishedReview(place, userId = user) }
                user
            }

        assertKeysetWalk<UserRankingRow>(
            expected = mine.sortedDescending(),
            idOf = { it.userId },
        ) { after ->
            nextRowOfMine(UserRankingSort.REVIEW_COUNT, after, mine.toSet())
        }
    }

    @Test
    fun `커서가 공유 리뷰 수 동률 경계에서 중복도 누락도 없다`() {
        // 공유 수가 전부 같은 넷. 리뷰 수는 일부러 제각각이라 축이 섞이면 순서가 깨진다
        val place = fixtures.newPlace()
        val mine =
            (1..4).map { n ->
                val user = fixtures.newUser("공유동률$n")
                val group = fixtures.newGroup(user)
                repeat(
                    2,
                ) { fixtures.shareReview(group, fixtures.newPublishedReview(place, userId = user).reviewId, user) }
                repeat(n) { fixtures.newPublishedReview(place, userId = user) }
                user
            }

        assertKeysetWalk<UserRankingRow>(
            expected = mine.sortedDescending(),
            idOf = { it.userId },
        ) { after ->
            nextRowOfMine(UserRankingSort.SHARED_REVIEW_COUNT, after, mine.toSet())
        }
    }

    @Test
    fun `공유 리뷰 수 정렬은 공유 수 내림차순이다`() {
        val place = fixtures.newPlace()
        // 리뷰 수와 공유 수의 순서를 일부러 반대로 둔다 — 축을 잘못 잡으면 순서가 뒤집힌다
        val users =
            listOf(3, 2, 1).map { shares ->
                val user = fixtures.newUser("공유정렬$shares")
                val group = fixtures.newGroup(user)
                repeat(
                    shares,
                ) { fixtures.shareReview(group, fixtures.newPublishedReview(place, userId = user).reviewId, user) }
                repeat(4 - shares) { fixtures.newPublishedReview(place, userId = user) }
                user
            }
        val mine = users.toSet()

        val bySharedCount = scanMine(UserRankingSort.SHARED_REVIEW_COUNT, mine)

        assertEquals(users, bySharedCount.map { it.userId })
        assertEquals(listOf(3, 2, 1), bySharedCount.map { it.sharedReviewCount })
    }

    /** 내 fixture만 골라 정렬 순서대로 모은다. */
    private fun scanMine(
        sort: UserRankingSort,
        mine: Set<Long>,
    ): List<UserRankingRow> {
        val rows = mutableListOf<UserRankingRow>()
        var cursor: UserRankingKey? = null
        repeat(MAX_SCAN_PAGES) {
            val slice = adapter.findUserRankings(UserRankingsQuery(sort = sort, after = cursor, limit = SCAN_PAGE_SIZE))
            slice.rows.filterTo(rows) { it.userId in mine }
            if (!slice.hasNext) return rows
            cursor = assertNotNull(slice.lastKey, "다음 페이지가 있는데 정렬 키가 없다")
        }
        fail("$MAX_SCAN_PAGES 페이지를 넘겨도 목록 끝에 닿지 못했다")
    }

    /**
     * `limit = 1`로 한 행씩 전진하되 다른 테스트가 만든 사용자는 건너뛴다 — 경계 판정은 그대로
     * 유지하면서 내 fixture만 순회한다.
     */
    private fun nextRowOfMine(
        sort: UserRankingSort,
        after: UserRankingRow?,
        mine: Set<Long>,
    ): List<UserRankingRow> {
        var cursor = after?.let { UserRankingKey(sortValueOf(sort, it), it.userId) }
        while (true) {
            val row =
                adapter
                    .findUserRankings(UserRankingsQuery(sort = sort, after = cursor, limit = 1))
                    .rows
                    .firstOrNull() ?: return emptyList()
            if (row.userId in mine) return listOf(row)
            cursor = UserRankingKey(sortValueOf(sort, row), row.userId)
        }
    }

    private fun sortValueOf(
        sort: UserRankingSort,
        row: UserRankingRow,
    ): Int =
        when (sort) {
            UserRankingSort.REVIEW_COUNT -> row.reviewCount
            UserRankingSort.SHARED_REVIEW_COUNT -> row.sharedReviewCount
        }

    private fun allUserIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        var cursor: UserRankingKey? = null
        repeat(MAX_SCAN_PAGES) {
            val slice =
                adapter.findUserRankings(
                    UserRankingsQuery(sort = UserRankingSort.REVIEW_COUNT, after = cursor, limit = SCAN_PAGE_SIZE),
                )
            slice.rows.forEach { ids += it.userId }
            if (!slice.hasNext) return ids
            cursor = assertNotNull(slice.lastKey, "다음 페이지가 있는데 정렬 키가 없다")
        }
        fail("$MAX_SCAN_PAGES 페이지를 넘겨도 목록 끝에 닿지 못했다")
    }

    private fun rowOf(userId: Long): UserRankingRow {
        var cursor: UserRankingKey? = null
        repeat(MAX_SCAN_PAGES) {
            val slice =
                adapter.findUserRankings(
                    UserRankingsQuery(sort = UserRankingSort.REVIEW_COUNT, after = cursor, limit = SCAN_PAGE_SIZE),
                )
            slice.rows.firstOrNull { row -> row.userId == userId }?.let { return it }
            if (!slice.hasNext) fail("userId=$userId 행을 찾지 못했다")
            cursor = assertNotNull(slice.lastKey, "다음 페이지가 있는데 정렬 키가 없다")
        }
        fail("$MAX_SCAN_PAGES 페이지를 넘겨도 userId=$userId 행이 없다")
    }

    private companion object {
        const val SCAN_PAGE_SIZE = 100
        const val MAX_SCAN_PAGES = 50
    }
}
