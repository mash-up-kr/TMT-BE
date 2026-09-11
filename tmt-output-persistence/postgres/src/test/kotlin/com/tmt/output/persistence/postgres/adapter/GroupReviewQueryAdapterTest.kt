package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 그룹 공유 리뷰 목록 (D_02 §3-2, TMT-222) — TMT-348 전수 커버.
 *
 * [GroupReviewQueryAdapter.existsGroup]·[GroupReviewQueryAdapter.isMember]는 게이트 판정(G1)의
 * 입력이라, 둘이 뒤집히면 남의 그룹 리뷰가 통째로 열리거나 내 그룹이 잠긴다.
 */
@Import(GroupReviewQueryAdapter::class)
class GroupReviewQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupReviewQueryAdapter

    @Test
    fun `있는 그룹과 없는 그룹을 가른다`() {
        val group = fixtures.newGroup(fixtures.newUser())

        assertTrue(adapter.existsGroup(group))
        assertFalse(adapter.existsGroup(-1L))
    }

    @Test
    fun `멤버 판정은 ACTIVE만 참이다`() {
        val member = fixtures.newUser()
        val left = fixtures.newUser()
        val stranger = fixtures.newUser()
        val group = fixtures.newGroup(member)
        fixtures.newMembership(group, member)
        fixtures.newMembership(group, left, status = "LEFT")

        assertTrue(adapter.isMember(group, member))
        assertFalse(adapter.isMember(group, left), "탈퇴한 사람은 멤버가 아니다")
        assertFalse(adapter.isMember(group, stranger))
    }

    @Test
    fun `그 그룹에 공유된 리뷰만 나온다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val other = fixtures.newGroup(owner)
        val shared = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        val elsewhere = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(2))
        fixtures.shareReview(group, shared.reviewId, shared.userId)
        fixtures.shareReview(other, elsewhere.reviewId, elsewhere.userId)

        val ids = rows(group).map { it.reviewId }

        assertEquals(listOf(shared.reviewId), ids)
        assertFalse(elsewhere.reviewId in ids)
    }

    @Test
    fun `삭제된 리뷰는 공유가 남아 있어도 안 나온다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val alive = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        val deleted = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(2), deletedAt = Instant.now())
        fixtures.shareReview(group, alive.reviewId, alive.userId)
        fixtures.shareReview(group, deleted.reviewId, deleted.userId)

        assertEquals(listOf(alive.reviewId), rows(group).map { it.reviewId })
    }

    @Test
    fun `커서가 동률 경계에서 중복도 누락도 없다`() {
        // created_at이 전부 같아 tie-breaker(review_id)만으로 갈린다
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val sameTime = t(9)
        val ids =
            (1..4).map {
                val r = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = sameTime)
                fixtures.shareReview(group, r.reviewId, r.userId)
                r.reviewId
            }

        assertKeysetWalk<ReviewCardRow>(
            expected = ids.sortedDescending(),
            idOf = { it.reviewId },
        ) { after ->
            adapter
                .findSharedReviewRows(
                    groupId = group,
                    afterCreatedAt = after?.createdAt,
                    afterReviewId = after?.reviewId,
                    viewerId = null,
                    viewerLatitude = null,
                    viewerLongitude = null,
                    limit = 1,
                ).rows
        }
    }

    @Test
    fun `좌표를 주면 거리가 채워지고 없으면 null이다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val place = fixtures.newPlace(latitude = SEOUL_LAT, longitude = SEOUL_LNG)
        val review = fixtures.newPublishedReview(place, createdAt = t(1))
        fixtures.shareReview(group, review.reviewId, review.userId)

        assertNull(rows(group).single().distanceMeters)

        val withCoords =
            adapter
                .findSharedReviewRows(group, null, null, null, SEOUL_LAT, SEOUL_LNG, 20)
                .rows
                .single()

        assertEquals(0, withCoords.distanceMeters, "같은 좌표라 0m다")
    }

    @Test
    fun `favorite은 조회자 기준이다`() {
        val owner = fixtures.newUser()
        val viewer = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place, createdAt = t(1))
        fixtures.shareReview(group, review.reviewId, review.userId)
        fixtures.addFavorite(viewer, place)

        assertFalse(rows(group).single().favorite, "비로그인은 false다")

        val seen = adapter.findSharedReviewRows(group, null, null, viewer, null, null, 20).rows.single()
        assertTrue(seen.favorite)
    }

    @Test
    fun `작성자의 업로드한 사진을 내린다 (V7)`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val author = fixtures.newUser()
        val s3Key = fixtures.attachProfileImage(author, legacyUrl = "https://kakao.example/legacy.jpg")
        val review = fixtures.newPublishedReview(fixtures.newPlace(), userId = author, createdAt = t(1))
        fixtures.shareReview(group, review.reviewId, review.userId)

        assertEquals(s3Key, rows(group).single().authorProfileImageS3Key)
    }

    private fun rows(groupId: Long) = adapter.findSharedReviewRows(groupId, null, null, null, null, null, 20).rows

    private fun t(seconds: Long): Instant = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(seconds)

    private companion object {
        const val SEOUL_LAT = 37.5666
        const val SEOUL_LNG = 126.9784
    }
}
