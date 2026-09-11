package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.output.persistence.UserRankingRow
import com.tmt.application.port.output.persistence.UserRankingsQuery
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `리뷰 수는 살아있는 리뷰만 세고 소유 그룹이 없으면 멤버 수는 0이다`() {
        val user = fixtures.newUser("랭킹없음")
        val place = fixtures.newPlace()
        fixtures.newPublishedReview(place, userId = user)
        fixtures.newPublishedReview(place, userId = user)
        fixtures.newPublishedReview(place, userId = user, deletedAt = java.time.Instant.now())

        val row = rowOf(user)

        assertEquals("랭킹없음", row.nickname)
        assertEquals(2, row.reviewCount)
        assertEquals(0, row.memberCount)
    }

    @Test
    fun `멤버 수는 소유한 그룹의 멤버 수 합이다`() {
        val owner = fixtures.newUser("랭킹주인")
        val other = fixtures.newUser("남의주인")
        setMemberCount(fixtures.newGroup(owner), 3)
        setMemberCount(fixtures.newGroup(owner), 5)
        setMemberCount(fixtures.newGroup(other), 9)

        assertEquals(8, rowOf(owner).memberCount)
        assertEquals(9, rowOf(other).memberCount)
    }

    @Test
    fun `리뷰가 없는 사용자도 목록에 들어간다`() {
        val user = fixtures.newUser("리뷰0")

        assertEquals(0, rowOf(user).reviewCount)
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
            nextRowOfMine(after, mine.toSet())
        }
    }

    /**
     * `limit = 1`로 한 행씩 전진하되 다른 테스트가 만든 사용자는 건너뛴다 — 경계 판정은 그대로
     * 유지하면서 내 fixture만 순회한다.
     */
    private fun nextRowOfMine(
        after: UserRankingRow?,
        mine: Set<Long>,
    ): List<UserRankingRow> {
        var cursor = after?.let { UserRankingKey(it.reviewCount, it.userId) }
        while (true) {
            val row =
                adapter
                    .findUserRankings(UserRankingsQuery(after = cursor, limit = 1))
                    .rows
                    .firstOrNull() ?: return emptyList()
            if (row.userId in mine) return listOf(row)
            cursor = UserRankingKey(row.reviewCount, row.userId)
        }
    }

    private fun rowOf(userId: Long): UserRankingRow {
        var cursor: UserRankingKey? = null
        repeat(MAX_SCAN_PAGES) {
            val slice = adapter.findUserRankings(UserRankingsQuery(after = cursor, limit = SCAN_PAGE_SIZE))
            slice.rows.firstOrNull { row -> row.userId == userId }?.let { return it }
            if (!slice.hasNext) fail("userId=$userId 행을 찾지 못했다")
            cursor = assertNotNull(slice.lastKey, "다음 페이지가 있는데 정렬 키가 없다")
        }
        fail("$MAX_SCAN_PAGES 페이지를 넘겨도 userId=$userId 행이 없다")
    }

    private fun setMemberCount(
        groupId: Long,
        memberCount: Int,
    ) {
        jdbcTemplate.update("UPDATE groups SET member_count = ? WHERE id = ?", memberCount, groupId)
    }

    private companion object {
        const val SCAN_PAGE_SIZE = 100
        const val MAX_SCAN_PAGES = 50
    }
}
