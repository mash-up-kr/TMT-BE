package com.tmt.output.persistence.postgres.adapter

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
 * 매장 추천 네이티브 쿼리 5종 (TMT-289).
 *
 * `DISTINCT ON` + 바깥 키셋, `NOT EXISTS` 후보 필터, `LEFT JOIN LATERAL` 최신 리뷰 —
 * 전부 단위 테스트가 못 잡는 것들이라 실제 SQL을 태운다.
 */
@Import(RecommendationQueryAdapter::class)
class RecommendationQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: RecommendationQueryAdapter

    @Test
    fun `같은 매장에 리뷰가 여러 건이어도 격자에는 한 칸이다`() {
        // 격자가 매장 단위인 이유 — 리뷰 단위로 두면 한 매장이 두 칸을 먹는다 (S6, J §5-1)
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        fixtures.newPublishedReview(place, user, createdAt = t(1))
        fixtures.newPublishedReview(place, user, createdAt = t(2))

        val rows = adapter.findReviewedPlaceRows(user, null, null, 20)

        assertEquals(listOf(place), rows.map { it.placeId })
    }

    @Test
    fun `격자는 마지막으로 리뷰한 순이다`() {
        val user = fixtures.newUser()
        val old = fixtures.newPlace()
        val recent = fixtures.newPlace()
        fixtures.newPublishedReview(old, user, createdAt = t(1))
        fixtures.newPublishedReview(recent, user, createdAt = t(5))

        val rows = adapter.findReviewedPlaceRows(user, null, null, 20)

        assertEquals(listOf(recent, old), rows.map { it.placeId })
    }

    @Test
    fun `같은 매장의 최신 리뷰 시각이 정렬 기준이다`() {
        // 오래된 매장에 방금 리뷰를 하나 더 쓰면 그 매장이 첫 칸으로 온다
        val user = fixtures.newUser()
        val a = fixtures.newPlace()
        val b = fixtures.newPlace()
        fixtures.newPublishedReview(a, user, createdAt = t(1))
        fixtures.newPublishedReview(b, user, createdAt = t(3))
        fixtures.newPublishedReview(a, user, createdAt = t(9))

        val rows = adapter.findReviewedPlaceRows(user, null, null, 20)

        assertEquals(listOf(a, b), rows.map { it.placeId })
    }

    @Test
    fun `격자 커서는 다음 페이지를 이어서 준다`() {
        val user = fixtures.newUser()
        val p1 = fixtures.newPlace()
        val p2 = fixtures.newPlace()
        val p3 = fixtures.newPlace()
        fixtures.newPublishedReview(p1, user, createdAt = t(1))
        fixtures.newPublishedReview(p2, user, createdAt = t(2))
        fixtures.newPublishedReview(p3, user, createdAt = t(3))

        val first = adapter.findReviewedPlaceRows(user, null, null, 2)
        assertEquals(listOf(p3, p2), first.map { it.placeId })

        val last = first.last()
        val next = adapter.findReviewedPlaceRows(user, last.latestReviewedAt, last.placeId, 2)

        assertEquals(listOf(p1), next.map { it.placeId })
    }

    @Test
    fun `미완성 저장과 삭제된 리뷰의 매장은 격자에 없다`() {
        // 리뷰가 된 것만 추천의 재료다 (R8·R6)
        val user = fixtures.newUser()
        val onlySaved = fixtures.newPlace()
        val deleted = fixtures.newPlace()
        fixtures.newSave(user, onlySaved)
        fixtures.newPublishedReview(deleted, user, deletedAt = Instant.now())

        val rows = adapter.findReviewedPlaceRows(user, null, null, 20)

        assertFalse(rows.any { it.placeId == onlySaved || it.placeId == deleted })
    }

    @Test
    fun `격자 썸네일은 그 매장 내 최신 리뷰의 첫 사진이다`() {
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        val older = fixtures.newPublishedReview(place, user, createdAt = t(1))
        val newer = fixtures.newPublishedReview(place, user, createdAt = t(5))
        fixtures.attachPhoto(older.saveId, fixtures.newMediaAsset(user), photoOrder = 0)
        val newerFirst = fixtures.newMediaAsset(user)
        fixtures.attachPhoto(newer.saveId, fixtures.newMediaAsset(user), photoOrder = 1)
        fixtures.attachPhoto(newer.saveId, newerFirst, photoOrder = 0)

        val row = adapter.findReviewedPlaceRows(user, null, null, 20).single { it.placeId == place }

        assertEquals(s3KeyOf(newerFirst), row.thumbnailS3Key)
    }

    @Test
    fun `사진 0장 리뷰의 매장도 격자에 남고 썸네일만 null이다`() {
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        fixtures.newPublishedReview(place, user, createdAt = t(1))

        val row = adapter.findReviewedPlaceRows(user, null, null, 20).single { it.placeId == place }

        assertNull(row.thumbnailS3Key)
    }

    @Test
    fun `검증은 내가 리뷰한 매장만 돌려준다`() {
        val me = fixtures.newUser()
        val other = fixtures.newUser()
        val mine = fixtures.newPlace()
        val theirs = fixtures.newPlace()
        fixtures.newPublishedReview(mine, me)
        fixtures.newPublishedReview(theirs, other)

        val found = adapter.findReviewedPlaceIdsAmong(me, listOf(mine, theirs))

        assertEquals(listOf(mine), found)
    }

    @Test
    fun `씨앗은 매장마다 내 최신 리뷰 1건이다`() {
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        fixtures.newPublishedReview(place, user, content = "예전", createdAt = t(1))
        fixtures.newPublishedReview(place, user, rating = 3, content = "최근", createdAt = t(5))

        val seed = adapter.findSeedPlaces(user, listOf(place)).single()

        assertEquals("최근", seed.content)
        assertEquals(3, seed.rating)
    }

    @Test
    fun `후보에는 내가 리뷰한 매장이 나오지 않는다`() {
        // review_count를 줘서 "리뷰 0건이라 빠진 것"과 구분한다 — 내가 썼다는 사실이 제외 사유다
        val user = fixtures.newUser()
        val reviewed = fixtures.newPlace(reviewCount = 5, ratingSum = 20)
        fixtures.newPublishedReview(reviewed, user)

        val candidates = adapter.findCandidatePlaces(user, listOf(reviewed), ALL)

        assertFalse(candidates.any { it.placeId == reviewed })
    }

    @Test
    fun `후보는 씨앗과 같은 카테고리를 앞에 둔다`() {
        // 카테고리 일치 표현식이 NULL이면 ORDER BY DESC가 NULL을 맨 앞에 둔다 — COALESCE가 그것을 막는다
        val user = fixtures.newUser()
        val seed = fixtures.newPlace(categoryId = "cat_meat")
        fixtures.newPublishedReview(seed, user)
        // 둘 다 리뷰가 있어야 후보에 남는다 — 리뷰 0건은 걸러진다
        val noCategory = fixtures.newPlace(categoryId = null, reviewCount = 99)
        val sameCategory = fixtures.newPlace(categoryId = "cat_meat", reviewCount = 1)

        val candidates = adapter.findCandidatePlaces(user, listOf(seed), ALL)
        val ranks = candidates.map { it.placeId }

        assertTrue(
            ranks.indexOf(sameCategory) < ranks.indexOf(noCategory),
            "같은 카테고리가 카테고리 없는 매장보다 앞이어야 한다: $ranks",
        )
    }

    @Test
    fun `리뷰 0건 매장은 후보에 없다`() {
        // 요약도 썸네일도 없는 카드는 "추천했다"는 사실만 남는다 (PR #106 리뷰)
        val user = fixtures.newUser()
        val seed = fixtures.newPlace()
        fixtures.newPublishedReview(seed, user)
        val fresh = fixtures.newPlace(reviewCount = 0, ratingSum = 0)

        val candidates = adapter.findCandidatePlaces(user, listOf(seed), ALL)

        assertFalse(candidates.any { it.placeId == fresh })
    }

    @Test
    fun `후보의 평균 별점은 rating_sum을 review_count로 나눈 값이다`() {
        val user = fixtures.newUser()
        val seed = fixtures.newPlace()
        fixtures.newPublishedReview(seed, user)
        val rated = fixtures.newPlace(reviewCount = 4, ratingSum = 18)

        val candidate = adapter.findCandidatePlaces(user, listOf(seed), ALL).single { it.placeId == rated }

        assertEquals(4.5, candidate.averageRating)
    }

    @Test
    fun `카드의 요약은 매장 최신 리뷰의 것이다`() {
        val place = fixtures.newPlace()
        val older = fixtures.newPublishedReview(place, createdAt = t(1))
        val newer = fixtures.newPublishedReview(place, createdAt = t(5))
        fixtures.addSummary(older.reviewId, pros = "예전 장점", cons = null)
        fixtures.addSummary(newer.reviewId, pros = "최근 장점", cons = "최근 단점")

        val row = assertNotNull(adapter.findRecommendedPlace(place))

        assertEquals(newer.reviewId, row.summaryReviewId)
        assertEquals("최근 장점", row.summaryPros)
    }

    @Test
    fun `최신 리뷰에 요약이 없으면 summaryReviewId가 null이다`() {
        // 요약이 있는 예전 리뷰로 대신 채우지 않는다 — A3는 최신 리뷰 1건을 그대로 쓰라고 한다
        val place = fixtures.newPlace()
        val older = fixtures.newPublishedReview(place, createdAt = t(1))
        fixtures.newPublishedReview(place, createdAt = t(5))
        fixtures.addSummary(older.reviewId)

        val row = assertNotNull(adapter.findRecommendedPlace(place))

        assertNull(row.summaryReviewId)
        assertNull(row.summaryPros)
    }

    @Test
    fun `리뷰가 없는 매장도 카드는 나오고 요약만 null이다`() {
        val place = fixtures.newPlace()

        val row = assertNotNull(adapter.findRecommendedPlace(place))

        assertEquals(place, row.placeId)
        assertNull(row.summaryReviewId)
        assertNull(row.thumbnailS3Key)
    }

    @Test
    fun `카드 썸네일은 매장 최신 리뷰의 첫 사진이다`() {
        val place = fixtures.newPlace()
        val older = fixtures.newPublishedReview(place, createdAt = t(1))
        val newer = fixtures.newPublishedReview(place, createdAt = t(5))
        fixtures.attachPhoto(older.saveId, fixtures.newMediaAsset(older.userId), photoOrder = 0)
        val expected = fixtures.newMediaAsset(newer.userId)
        fixtures.attachPhoto(newer.saveId, expected, photoOrder = 0)

        val row = assertNotNull(adapter.findRecommendedPlace(place))

        assertEquals(s3KeyOf(expected), row.thumbnailS3Key)
    }

    @Test
    fun `없는 매장은 null이다`() {
        assertNull(adapter.findRecommendedPlace(-1L))
    }

    companion object {
        /**
         * 후보 쿼리는 `review_count DESC`라 작은 limit으로는 방금 만든 리뷰 0건 매장이 잘린다.
         * 컨테이너를 모듈이 공유해 앞선 실행의 place가 쌓여 있으므로(TMT-295) 전량을 받아
         * 내가 만든 id로만 단언한다.
         */
        private const val ALL = 100_000
    }

    private fun t(seconds: Long): Instant = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(seconds)

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT s3_key FROM media_asset WHERE id = ?",
            String::class.java,
            mediaAssetId,
        )!!
}
