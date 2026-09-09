package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.NewReviewSummary
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 요약 대기 목록 (TMT-232) — TMT-348 전수 커버.
 *
 * 이 쿼리는 사용자로 좁혀지지 않아 **전역 조회**다. 컨테이너를 공유하므로(TMT-295)
 * 전체 건수에 단언하지 않고 **내가 만든 리뷰가 목록에 있나/없나**로만 본다.
 */
@Import(ReviewAiSummaryAdapter::class)
class ReviewAiSummaryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: ReviewAiSummaryAdapter

    @Test
    fun `요약이 없는 리뷰가 대기 목록에 있다`() {
        val place = fixtures.newPlace(name = "요약대기가게")
        val review = fixtures.newPublishedReview(place, content = "요약할 본문")

        val row = pendingOf(review.reviewId)

        assertEquals("요약대기가게", row.placeName)
        assertEquals("요약할 본문", row.content)
        assertEquals(place, row.placeId)
    }

    @Test
    fun `요약이 생기면 대기 목록에서 빠진다`() {
        // A2 — review_ai_summary 행이 있으면 요약된 것이다
        val review = fixtures.newPublishedReview(fixtures.newPlace(), content = "본문")
        assertTrue(isPending(review.reviewId))

        adapter.saveSummaries(listOf(NewReviewSummary(review.reviewId, "좋아요", null, "test-model")))

        assertFalse(isPending(review.reviewId))
    }

    @Test
    fun `삭제된 리뷰는 대기 목록에 없다`() {
        val review = fixtures.newPublishedReview(fixtures.newPlace(), content = "본문", deletedAt = Instant.now())

        assertFalse(isPending(review.reviewId))
    }

    @Test
    fun `본문이 없는 리뷰는 대기 목록에 없다`() {
        // 요약할 텍스트가 없다
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        val saveId = fixtures.newSave(user, place, content = null)
        val reviewId = fixtures.newReview(saveId, user, place)

        assertFalse(isPending(reviewId))
    }

    @Test
    fun `공백만인 본문은 대기 목록에 없다 (TMT-392)`() {
        // 요약할 텍스트가 없는데 LLM을 부르면 매 배치 헛도는 호출이 된다
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        val saveId = fixtures.newSave(user, place, content = "   ")
        val reviewId = fixtures.newReview(saveId, user, place)

        assertFalse(isPending(reviewId))
    }

    @Test
    fun `요약 불가로 기록된 리뷰는 대기 목록에서 빠진다 (TMT-392)`() {
        // pros·cons 둘 다 null인 행 = "봤는데 요약할 내용이 없다". 행이 있으니 다음 배치가 다시 집지 않는다
        val review = fixtures.newPublishedReview(fixtures.newPlace(), content = "ㅂㅈㄷ")
        assertTrue(isPending(review.reviewId))

        adapter.saveSummaries(listOf(NewReviewSummary(review.reviewId, pros = null, cons = null, "test-model")))

        assertFalse(isPending(review.reviewId))
    }

    private fun pending() = adapter.findPendingReviews(ALL)

    private fun isPending(reviewId: Long) = pending().any { it.reviewId == reviewId }

    private fun pendingOf(reviewId: Long) = pending().single { it.reviewId == reviewId }

    private companion object {
        /** 전역 쿼리라 앞선 실행분이 쌓여 있다 — 전량을 받아 내 id로만 좁힌다 */
        const val ALL = 100_000
    }
}
