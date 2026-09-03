package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 가게 상세·리뷰 목록·찜 쿼리 (TMT-229).
 *
 * 리뷰 목록은 매장 id로 좁혀 읽으므로 매장을 새로 만들면 다른 테스트와 섞이지 않는다.
 * 찜은 `@Modifying`이라 트랜잭션이 필요한데 이 슬라이스는 롤백과 함께 트랜잭션도 껐다 —
 * [TransactionTemplate]으로 호출부의 트랜잭션을 대신 연다.
 */
class PlaceQueryRepositoryTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: PlaceQueryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transaction by lazy { TransactionTemplate(transactionManager) }

    @Test
    fun `상세는 좌표와 집계를 넣은 그대로 돌려준다`() {
        val place =
            fixtures.newPlace(
                name = "상세매장",
                latitude = 37.5,
                longitude = 127.0,
                categoryId = "cat_korean",
                reviewCount = 3,
                ratingSum = 12,
            )

        val detail = repository.findDetail(place, null)!!

        assertEquals("상세매장", detail.getName())
        assertEquals("cat_korean", detail.getCategoryId())
        assertEquals(3, detail.getReviewCount())
        assertEquals(12L, detail.getRatingSum())
        assertEquals(37.5, detail.getLatitude(), 1e-6)
        assertEquals(127.0, detail.getLongitude(), 1e-6)
        assertNull(detail.getPhoneNumber())
        assertFalse(detail.getFavorite())
    }

    @Test
    fun `없는 매장은 null이다`() {
        assertNull(repository.findDetail(Long.MAX_VALUE, null))
    }

    @Test
    fun `상세의 찜은 보는 사람 기준이다`() {
        val place = fixtures.newPlace()
        val viewer = fixtures.newUser()
        val stranger = fixtures.newUser()
        fixtures.deleteFavorite(viewer, place)
        fixtures.addFavorite(viewer, place)

        assertTrue(repository.findDetail(place, viewer)!!.getFavorite())
        assertFalse(repository.findDetail(place, stranger)!!.getFavorite())
    }

    @Test
    fun `리뷰 목록은 최신순이고 삭제된 리뷰는 빠진다`() {
        val place = fixtures.newPlace()
        val now = Instant.now()
        val older = fixtures.newPublishedReview(place, createdAt = now.minusSeconds(600)).reviewId
        val newer = fixtures.newPublishedReview(place, createdAt = now.minusSeconds(60)).reviewId
        fixtures.newPublishedReview(place, createdAt = now, deletedAt = now)

        val rows = repository.findPlaceReviewRows(place, null, null, null, null, null, 50)

        assertEquals(listOf(newer, older), rows.map { it.getReviewId() })
    }

    @Test
    fun `리뷰 목록 커서는 내림차순 경계에서 겹치지 않는다`() {
        val place = fixtures.newPlace()
        val now = Instant.now()
        // created_at이 같은 두 건 — (created_at, id) 행 비교의 tie-breaker만 남는다
        val sameTime = (1..2).map { fixtures.newPublishedReview(place, createdAt = now).reviewId }.sortedDescending()
        val oldest = fixtures.newPublishedReview(place, createdAt = now.minusSeconds(600)).reviewId

        val first = repository.findPlaceReviewRows(place, null, null, null, null, null, 2)
        assertEquals(sameTime, first.map { it.getReviewId() })

        val last = first.last()
        val second =
            repository.findPlaceReviewRows(place, last.getCreatedAt(), last.getReviewId(), null, null, null, 2)
        assertEquals(listOf(oldest), second.map { it.getReviewId() })
    }

    @Test
    fun `보는 사람 좌표가 없으면 거리는 null이다`() {
        val place = fixtures.newPlace(latitude = 37.5, longitude = 127.0)
        fixtures.newPublishedReview(place)

        val withoutCoordinate = repository.findPlaceReviewRows(place, null, null, null, null, null, 10)
        assertNull(withoutCoordinate.single().getDistanceMeters())

        val withCoordinate = repository.findPlaceReviewRows(place, null, null, null, 37.501, 127.0, 10)
        val distance = withCoordinate.single().getDistanceMeters()!!
        assertTrue(distance in 100..120, "약 111m를 기대했는데 ${distance}m가 나왔다")
    }

    @Test
    fun `대표 사진은 리뷰 최신순 photo_order 순으로 나온다`() {
        val place = fixtures.newPlace()
        val old = fixtures.newPublishedReview(place, createdAt = Instant.now().minusSeconds(600))
        val recent = fixtures.newPublishedReview(place, createdAt = Instant.now())
        fixtures.attachPhoto(old.saveId, fixtures.newMediaAsset(old.userId), photoOrder = 0)
        fixtures.attachPhoto(recent.saveId, fixtures.newMediaAsset(recent.userId), photoOrder = 1)
        fixtures.attachPhoto(recent.saveId, fixtures.newMediaAsset(recent.userId), photoOrder = 0)

        val photos = repository.findRecentPhotos(place, 10)

        assertEquals(listOf(recent.reviewId, recent.reviewId, old.reviewId), photos.map { it.getReviewId() })
    }

    @Test
    fun `찜은 두 번 눌러도 한 행이고 두 번째는 0을 돌려준다`() {
        val place = fixtures.newPlace()
        val user = fixtures.newUser()
        fixtures.deleteFavorite(user, place)

        val inserted = transaction.execute { repository.addFavorite(user, place) }
        val duplicated = transaction.execute { repository.addFavorite(user, place) }

        assertEquals(1, inserted)
        assertEquals(0, duplicated, "ON CONFLICT DO NOTHING이 두 번째 삽입을 무시해야 한다 (F2)")
        assertEquals(1, favoriteCount(user, place))
    }

    @Test
    fun `찜 해제는 없는 행을 지워도 0으로 끝난다`() {
        val place = fixtures.newPlace()
        val user = fixtures.newUser()
        fixtures.deleteFavorite(user, place)

        assertEquals(0, transaction.execute { repository.removeFavorite(user, place) })

        transaction.execute { repository.addFavorite(user, place) }
        assertEquals(1, transaction.execute { repository.removeFavorite(user, place) })
        assertEquals(0, favoriteCount(user, place))
    }

    @Test
    fun `상세와 리뷰 목록은 같은 매장을 본다`() {
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place)

        val detail = repository.findDetail(place, null)
        val rows = repository.findPlaceReviewRows(place, null, null, null, null, null, 10)

        assertNotNull(detail)
        assertEquals(place, rows.single().getPlaceId())
        assertEquals(review.saveId, rows.single().getSaveId())
    }

    private fun favoriteCount(
        userId: Long,
        placeId: Long,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM place_favorite WHERE user_id = ? AND place_id = ?",
            Int::class.java,
            userId,
            placeId,
        )!!
}
