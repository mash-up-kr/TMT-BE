package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 리뷰 단건 읽기 (I §6-3·§6-4, TMT-226) — TMT-348 전수 커버.
 *
 * 두 조회 모두 **삭제된 리뷰를 없는 것으로 본다** (D6). 그 조건이 빠지면 지운 리뷰의
 * 상세가 열리고, 삭제 경로에서는 이미 되돌린 집계를 한 번 더 되돌린다.
 */
@Import(ReviewQueryAdapter::class)
class ReviewQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: ReviewQueryAdapter

    @Test
    fun `상세는 작성자와 매장을 함께 준다`() {
        val author = fixtures.newUser("리뷰작성자")
        val place = fixtures.newPlace(name = "상세가게", roadAddress = "서울특별시 중구 세종대로 2")
        val review = fixtures.newPublishedReview(place, author, rating = 4, content = "본문이다")

        val row = assertNotNull(adapter.findReviewDetail(review.reviewId))

        assertEquals(author, row.authorId)
        assertEquals("리뷰작성자", row.authorNickname)
        assertEquals("상세가게", row.placeName)
        assertEquals("서울특별시 중구 세종대로 2", row.placeRoadAddress)
        assertEquals(4, row.rating)
        assertEquals("본문이다", row.content)
        assertEquals(review.saveId, row.saveId)
    }

    @Test
    fun `삭제된 리뷰의 상세는 null이다`() {
        val review = fixtures.newPublishedReview(fixtures.newPlace(), deletedAt = Instant.now())

        assertNull(adapter.findReviewDetail(review.reviewId))
    }

    @Test
    fun `없는 리뷰의 상세는 null이다`() {
        assertNull(adapter.findReviewDetail(-1L))
    }

    @Test
    fun `삭제 정보는 소유자와 되돌릴 별점을 준다`() {
        val author = fixtures.newUser()
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place, author, rating = 3)

        val row = assertNotNull(adapter.findReviewForDeletion(review.reviewId))

        assertEquals(author, row.userId)
        assertEquals(place, row.placeId)
        assertEquals(review.saveId, row.saveId)
        assertEquals(3, row.rating, "매장 집계를 되돌릴 값이다")
    }

    @Test
    fun `이미 삭제된 리뷰는 삭제 정보도 null이다`() {
        // 두 번 지우면 집계가 두 번 되돌아간다 — 여기서 끊는다 (R6)
        val review = fixtures.newPublishedReview(fixtures.newPlace(), deletedAt = Instant.now())

        assertNull(adapter.findReviewForDeletion(review.reviewId))
    }
}
