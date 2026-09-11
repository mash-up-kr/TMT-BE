package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewShareRow
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 공유 선택 목록 (H §3-1).
 *
 * 이 파일이 생긴 계기는 TMT-352다 — 썸네일 조인이 `INNER`라 **사진 0장 리뷰가 목록에서
 * 조용히 사라졌다.** 500이 아니라 행이 없어지는 실패라 컨트롤러 테스트로는 안 잡힌다.
 */
@Import(GroupShareQueryAdapter::class)
class GroupShareQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupShareQueryAdapter

    @Test
    fun `사진 없이 쓴 리뷰도 공유 선택 목록에 나온다`() {
        // C4-1(TMT-268)로 사진 0장 리뷰가 성립한다. INNER JOIN LATERAL이던 시절엔 여기서 사라졌다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val place = fixtures.newPlace(name = "사진없는가게")
        val review = fixtures.newPublishedReview(place, user, createdAt = t(1))

        val rows = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows

        val row = assertNotNull(rows.singleOrNull { it.reviewId == review.reviewId }, "행이 사라졌다")
        assertNull(row.thumbnailS3Key)
        assertEquals("사진없는가게", row.placeName)
    }

    @Test
    fun `사진 있는 리뷰는 첫 사진이 썸네일이다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        val first = fixtures.newMediaAsset(user)
        fixtures.attachPhoto(review.saveId, fixtures.newMediaAsset(user), photoOrder = 1)
        fixtures.attachPhoto(review.saveId, first, photoOrder = 0)

        val row = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows.single()

        assertEquals(s3KeyOf(first), row.thumbnailS3Key)
    }

    @Test
    fun `사진 있는 리뷰와 없는 리뷰가 한 목록에 최신순으로 섞인다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val withPhoto = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        fixtures.attachPhoto(withPhoto.saveId, fixtures.newMediaAsset(user), photoOrder = 0)
        val without = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(5))

        val rows = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows

        assertEquals(listOf(without.reviewId, withPhoto.reviewId), rows.map { it.reviewId })
        assertNull(rows.first().thumbnailS3Key)
        assertNotNull(rows.last().thumbnailS3Key)
    }

    @Test
    fun `sharedCount가 목록의 공유 표시 개수와 맞는다`() {
        // 목록은 LATERAL 조인을 타고 sharedCount는 별도 쿼리다 — 조인이 행을 떨어뜨리면 둘이 어긋난다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val shared = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        val alsoShared = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(2))
        fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(3))
        // 공유된 둘 중 하나만 사진이 있다
        fixtures.attachPhoto(shared.saveId, fixtures.newMediaAsset(user), photoOrder = 0)
        fixtures.shareReview(group, shared.reviewId, user)
        fixtures.shareReview(group, alsoShared.reviewId, user)

        val rows = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows
        val sharedCount = adapter.countSharedByUser(group, user)

        assertEquals(3, rows.size, "사진 없는 리뷰가 빠지면 3이 아니다")
        assertEquals(sharedCount, rows.count { it.isShared })
        assertEquals(2, sharedCount)
    }

    @Test
    fun `공유 여부는 그 그룹 기준이다`() {
        val user = fixtures.newUser()
        val here = fixtures.newGroup(user)
        val elsewhere = fixtures.newGroup(user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        fixtures.shareReview(elsewhere, review.reviewId, user)

        val row = adapter.findMyReviewsWithShared(here, user, null, null, 20).rows.single()

        assertEquals(false, row.isShared)
        assertEquals(0, adapter.countSharedByUser(here, user))
    }

    @Test
    fun `남의 리뷰와 삭제된 리뷰는 목록에 없다`() {
        val user = fixtures.newUser()
        val other = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val mine = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        val theirs = fixtures.newPublishedReview(fixtures.newPlace(), other, createdAt = t(2))
        val deleted =
            fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(3), deletedAt = Instant.now())

        val ids = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows.map { it.reviewId }

        assertEquals(listOf(mine.reviewId), ids)
        assertTrue(theirs.reviewId !in ids && deleted.reviewId !in ids)
    }

    @Test
    fun `사진 없는 리뷰를 사이에 두고도 커서가 이어진다`() {
        // INNER 시절엔 가운데 행이 사라져 페이지 경계가 통째로 밀렸다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val r1 = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        val r2 = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(2))
        val r3 = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(3))
        fixtures.attachPhoto(r3.saveId, fixtures.newMediaAsset(user), photoOrder = 0)

        val first = adapter.findMyReviewsWithShared(group, user, null, null, 2)
        assertEquals(listOf(r3.reviewId, r2.reviewId), first.rows.map { it.reviewId })
        assertTrue(first.hasNext)

        val last = first.rows.last()
        val next = adapter.findMyReviewsWithShared(group, user, last.createdAt, last.reviewId, 2)

        assertEquals(listOf(r1.reviewId), next.rows.map { it.reviewId })
    }

    @Test
    fun `groupId가 없으면 공유 후보 목록이다 - 같은 행 같은 순서에 공유 표시만 없다`() {
        // 그룹 생성 5단계는 그룹이 아직 없어 groupId 없이 부른다 (TMT-428)
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val shared = fixtures.newPublishedReview(fixtures.newPlace(name = "공유중"), user, createdAt = t(1))
        val notShared = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(2))
        fixtures.attachPhoto(shared.saveId, fixtures.newMediaAsset(user), photoOrder = 0)
        fixtures.shareReview(group, shared.reviewId, user)

        val withGroup = adapter.findMyReviewsWithShared(group, user, null, null, 20).rows
        val candidates = adapter.findMyReviewsWithShared(null, user, null, null, 20).rows

        assertEquals(withGroup.map { it.reviewId }, candidates.map { it.reviewId })
        assertEquals(listOf(notShared.reviewId, shared.reviewId), candidates.map { it.reviewId })
        assertTrue(candidates.none { it.isShared }, "물어볼 그룹이 없으니 전부 false여야 한다")
        // 나머지 열은 그룹 조회와 같아야 한다 — 후보 화면도 매장명·본문·썸네일을 그린다
        val candidate = candidates.last()
        assertEquals("공유중", candidate.placeName)
        assertNotNull(candidate.thumbnailS3Key)
        assertEquals(withGroup.last().content, candidate.content)
    }

    @Test
    fun `공유 후보 목록에도 남의 리뷰와 삭제된 리뷰는 없다`() {
        val user = fixtures.newUser()
        val other = fixtures.newUser()
        val mine = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        fixtures.newPublishedReview(fixtures.newPlace(), other, createdAt = t(2))
        fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(3), deletedAt = Instant.now())

        val ids = adapter.findMyReviewsWithShared(null, user, null, null, 20).rows.map { it.reviewId }

        assertEquals(listOf(mine.reviewId), ids)
    }

    @Test
    fun `공유 후보 커서가 같은 시각 행을 사이에 두고도 중복도 누락도 없다`() {
        val user = fixtures.newUser()
        // 같은 createdAt 3건 — tie-breaker(reviewId)가 빠지면 여기서 무너진다
        val tied = (1..3).map { fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(2)) }
        val older = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        val newer = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(3))
        val expected =
            listOf(newer.reviewId) + tied.map { it.reviewId }.sortedDescending() + listOf(older.reviewId)

        assertKeysetWalk(
            expected = expected,
            idOf = { row: ReviewShareRow -> row.reviewId },
            page = { after -> adapter.findMyReviewsWithShared(null, user, after?.createdAt, after?.reviewId, 1).rows },
        )
    }

    @Test
    fun `공유 선택 커서가 같은 시각 행을 사이에 두고도 중복도 누락도 없다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        val tied = (1..3).map { fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(2)) }
        val older = fixtures.newPublishedReview(fixtures.newPlace(), user, createdAt = t(1))
        fixtures.shareReview(group, tied.first().reviewId, user)
        val expected = tied.map { it.reviewId }.sortedDescending() + listOf(older.reviewId)

        assertKeysetWalk(
            expected = expected,
            idOf = { row: ReviewShareRow -> row.reviewId },
            page = { after -> adapter.findMyReviewsWithShared(group, user, after?.createdAt, after?.reviewId, 1).rows },
        )
    }

    @Test
    fun `목록에 남의 리뷰가 섞이면 그 id를 돌려준다`() {
        // H §3-2 — 하나라도 있으면 REVIEW_NOT_FOUND다
        val me = fixtures.newUser()
        val other = fixtures.newUser()
        val mine = fixtures.newPublishedReview(fixtures.newPlace(), me)
        val theirs = fixtures.newPublishedReview(fixtures.newPlace(), other)

        assertEquals(listOf(theirs.reviewId), adapter.findNotMine(me, listOf(mine.reviewId, theirs.reviewId)))
        assertEquals(emptyList(), adapter.findNotMine(me, listOf(mine.reviewId)))
    }

    @Test
    fun `없는 리뷰도 내 것이 아닌 것으로 본다`() {
        val me = fixtures.newUser()

        assertEquals(listOf(-1L), adapter.findNotMine(me, listOf(-1L)))
    }

    @Test
    fun `삭제된 내 리뷰도 내 것이 아닌 것으로 본다`() {
        val me = fixtures.newUser()
        val deleted = fixtures.newPublishedReview(fixtures.newPlace(), me, deletedAt = Instant.now())

        assertEquals(listOf(deleted.reviewId), adapter.findNotMine(me, listOf(deleted.reviewId)))
    }

    @Test
    fun `빈 목록은 쿼리에 닿지 않는다`() {
        // IN () 는 SQL 문법 오류라 어댑터가 먼저 끊는다
        assertEquals(emptyList(), adapter.findNotMine(fixtures.newUser(), emptyList()))
    }

    @Test
    fun `현재 내 공유 집합을 돌려준다`() {
        val me = fixtures.newUser()
        val other = fixtures.newUser()
        val group = fixtures.newGroup(me)
        val a = fixtures.newPublishedReview(fixtures.newPlace(), me)
        val b = fixtures.newPublishedReview(fixtures.newPlace(), me)
        val theirs = fixtures.newPublishedReview(fixtures.newPlace(), other)
        fixtures.shareReview(group, a.reviewId, me)
        fixtures.shareReview(group, b.reviewId, me)
        fixtures.shareReview(group, theirs.reviewId, other)

        val ids = adapter.findSharedReviewIds(group, me)

        assertEquals(listOf(a.reviewId, b.reviewId).sorted(), ids.sorted())
    }

    private fun t(seconds: Long): Instant = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(seconds)

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT s3_key FROM media_asset WHERE id = ?",
            String::class.java,
            mediaAssetId,
        )!!
}
