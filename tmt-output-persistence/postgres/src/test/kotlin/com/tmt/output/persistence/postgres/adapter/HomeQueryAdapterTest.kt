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
 * 홈 네이티브 쿼리 4종 (A 명세, TMT-230) — TMT-348 전수 커버.
 *
 * 피드는 **가입한 그룹에 공유된 리뷰**만 보므로 사용자마다 완전히 격리된다. 새 사용자·새 그룹으로
 * 시작하면 컨테이너에 남은 앞선 실행의 데이터가 섞이지 않는다.
 */
@Import(HomeQueryAdapter::class)
class HomeQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: HomeQueryAdapter

    @Test
    fun `닉네임을 읽고 없는 사용자는 null이다`() {
        val user = fixtures.newUser("홈사용자")

        assertEquals("홈사용자", adapter.findNickname(user))
        assertNull(adapter.findNickname(-1L))
    }

    @Test
    fun `내 그룹은 가입 오래된 순이다`() {
        val user = fixtures.newUser()
        val second = fixtures.newGroup(user)
        val first = fixtures.newGroup(user)
        fixtures.newMembership(first, user, joinedAt = t(1))
        fixtures.newMembership(second, user, joinedAt = t(5))

        assertEquals(listOf(first, second), adapter.findMyGroups(user).map { it.groupId })
    }

    @Test
    fun `탈퇴한 그룹은 내 그룹에 없다`() {
        val user = fixtures.newUser()
        val stayed = fixtures.newGroup(user)
        val left = fixtures.newGroup(user)
        fixtures.newMembership(stayed, user, joinedAt = t(1))
        fixtures.newMembership(left, user, joinedAt = t(2), status = "LEFT")

        assertEquals(listOf(stayed), adapter.findMyGroups(user).map { it.groupId })
    }

    @Test
    fun `피드는 가입한 그룹에 공유된 리뷰만 본다`() {
        val user = fixtures.newUser()
        val myGroup = fixtures.newGroup(user)
        fixtures.newMembership(myGroup, user)
        val place = fixtures.newPlace()
        val shared = fixtures.newPublishedReview(place, createdAt = t(2))
        val notShared = fixtures.newPublishedReview(place, createdAt = t(3))
        fixtures.shareReview(myGroup, shared.reviewId, shared.userId)

        val ids = adapter.findFeedRowsByRecency(user, null, null, 20).rows.map { it.reviewId }

        assertEquals(listOf(shared.reviewId), ids)
        assertFalse(notShared.reviewId in ids)
    }

    @Test
    fun `같은 리뷰가 여러 그룹에 공유돼도 피드에 한 번만 나온다`() {
        // G19 — EXISTS로 보는 이유다. JOIN이면 공유 수만큼 행이 늘어난다
        val user = fixtures.newUser()
        val g1 = fixtures.newGroup(user)
        val g2 = fixtures.newGroup(user)
        fixtures.newMembership(g1, user)
        fixtures.newMembership(g2, user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        fixtures.shareReview(g1, review.reviewId, review.userId)
        fixtures.shareReview(g2, review.reviewId, review.userId)

        val ids = adapter.findFeedRowsByRecency(user, null, null, 20).rows.map { it.reviewId }

        assertEquals(listOf(review.reviewId), ids)
    }

    @Test
    fun `최신순 커서가 동률 경계에서 중복도 누락도 없다`() {
        // 같은 시각에 만든 리뷰 넷 — created_at이 전부 같아 tie-breaker(review_id)만으로 갈린다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        fixtures.newMembership(group, user)
        val sameTime = t(7)
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
            adapter.findFeedRowsByRecency(user, after?.createdAt, after?.reviewId, 1).rows
        }
    }

    @Test
    fun `거리순 커서가 동률 경계에서 중복도 누락도 없다`() {
        // 같은 매장의 리뷰 넷 — 거리가 전부 같아 tie-breaker만으로 갈린다
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        fixtures.newMembership(group, user)
        val place = fixtures.newPlace()
        val ids =
            (1..4).map {
                val r = fixtures.newPublishedReview(place, createdAt = t(it.toLong()))
                fixtures.shareReview(group, r.reviewId, r.userId)
                r.reviewId
            }

        assertKeysetWalk<ReviewCardRow>(
            expected = ids.sorted(),
            idOf = { it.reviewId },
        ) { after ->
            adapter
                .findFeedRowsByDistance(
                    userId = user,
                    latitude = SEOUL_LAT,
                    longitude = SEOUL_LNG,
                    afterDistanceMeters = after?.distanceMeters,
                    afterReviewId = after?.reviewId,
                    limit = 1,
                ).rows
        }
    }

    @Test
    fun `최신순 경로는 거리를 계산하지 않는다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        fixtures.newMembership(group, user)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        fixtures.shareReview(group, review.reviewId, review.userId)

        val row = adapter.findFeedRowsByRecency(user, null, null, 20).rows.single()

        assertNull(row.distanceMeters)
        assertTrue(row.placeName.isNotBlank())
    }

    @Test
    fun `hasNext는 limit을 넘겼을 때만 참이다`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        fixtures.newMembership(group, user)
        repeat(2) {
            val r = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(it.toLong()))
            fixtures.shareReview(group, r.reviewId, r.userId)
        }

        assertTrue(adapter.findFeedRowsByRecency(user, null, null, 1).hasNext)
        assertFalse(adapter.findFeedRowsByRecency(user, null, null, 2).hasNext)
    }

    @Test
    fun `피드 두 경로 모두 작성자의 업로드한 사진을 내린다 (V7)`() {
        val user = fixtures.newUser()
        val group = fixtures.newGroup(user)
        fixtures.newMembership(group, user)
        val author = fixtures.newUser()
        val s3Key = fixtures.attachProfileImage(author, legacyUrl = "https://kakao.example/legacy.jpg")
        val place = fixtures.newPlace(latitude = SEOUL_LAT, longitude = SEOUL_LNG)
        val review = fixtures.newPublishedReview(place, userId = author, createdAt = t(1))
        fixtures.shareReview(group, review.reviewId, review.userId)

        val byRecency = adapter.findFeedRowsByRecency(user, null, null, 20).rows.single()
        val byDistance =
            adapter
                .findFeedRowsByDistance(
                    userId = user,
                    latitude = SEOUL_LAT,
                    longitude = SEOUL_LNG,
                    afterDistanceMeters = null,
                    afterReviewId = null,
                    limit = 20,
                ).rows
                .single()

        assertEquals(s3Key, byRecency.authorProfileImageS3Key)
        assertEquals(s3Key, byDistance.authorProfileImageS3Key)
    }

    private fun t(seconds: Long): Instant = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(seconds)

    private companion object {
        const val SEOUL_LAT = 37.5666
        const val SEOUL_LNG = 126.9784
    }
}
