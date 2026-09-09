package com.tmt.application.domain.aisummary

import com.tmt.application.port.output.llm.LlmSummaryResult
import com.tmt.application.port.output.llm.PlaceReviewsToSummarize
import com.tmt.application.port.output.llm.ReviewSummaryLlmPort
import com.tmt.application.port.output.persistence.NewReviewSummary
import com.tmt.application.port.output.persistence.PendingReviewSummary
import com.tmt.application.port.output.persistence.ReviewAiSummaryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReviewSummaryServiceTest {
    private val summaryPort = mockk<ReviewAiSummaryPort>(relaxed = true)
    private val llmPort = mockk<ReviewSummaryLlmPort>()
    private val service = ReviewSummaryService(summaryPort, llmPort, batchSize = 100)

    private fun pending(
        reviewId: Long,
        placeId: Long,
        placeName: String = "가게$placeId",
    ) = PendingReviewSummary(reviewId, placeId, placeName, rating = 5, content = "리뷰$reviewId")

    private fun result(
        vararg summaries: LlmSummaryResult.ReviewSummary,
        model: String = "groq/test",
    ) = LlmSummaryResult(model, summaries.toList())

    @Test
    fun `같은 매장의 리뷰를 묶어 한 번 호출한다`() {
        every { summaryPort.findPendingReviews(any()) } returns
            listOf(pending(1, placeId = 10), pending(2, placeId = 10), pending(3, placeId = 20))
        val requests = mutableListOf<PlaceReviewsToSummarize>()
        every { llmPort.summarize(capture(requests)) } answers {
            result(
                *requests
                    .last()
                    .reviews
                    .map { LlmSummaryResult.ReviewSummary(it.reviewId, "좋아요", null) }
                    .toTypedArray(),
            )
        }

        val filled = service.summarizePending()

        assertEquals(3, filled)
        assertEquals(2, requests.size) // 매장 10 한 번 + 매장 20 한 번
        assertEquals(listOf(1L, 2L), requests.first { it.reviews.size == 2 }.reviews.map { it.reviewId })
    }

    @Test
    fun `한 매장의 실패가 다른 매장을 막지 않는다`() {
        every { summaryPort.findPendingReviews(any()) } returns
            listOf(pending(1, placeId = 10), pending(2, placeId = 20))
        every { llmPort.summarize(match { it.reviews.first().reviewId == 1L }) } throws
            IllegalStateException("프로바이더 소진")
        every { llmPort.summarize(match { it.reviews.first().reviewId == 2L }) } returns
            result(LlmSummaryResult.ReviewSummary(2, "좋아요", null))

        assertEquals(1, service.summarizePending())
    }

    @Test
    fun `요청하지 않은 reviewId는 버린다 - LLM이 지어낸 id로 남의 요약을 덮지 않는다`() {
        every { summaryPort.findPendingReviews(any()) } returns listOf(pending(1, placeId = 10))
        every { llmPort.summarize(any()) } returns
            result(
                LlmSummaryResult.ReviewSummary(1, "좋아요", null),
                LlmSummaryResult.ReviewSummary(999, "환각", null),
            )
        val saved = slot<List<NewReviewSummary>>()
        every { summaryPort.saveSummaries(capture(saved)) } returns Unit

        assertEquals(1, service.summarizePending())
        assertEquals(listOf(1L), saved.captured.map { it.reviewId })
    }

    @Test
    fun `pros와 cons가 모두 비면 요약 불가로 기록한다 - 다음 배치가 다시 부르지 않는다 (TMT-392)`() {
        // 예전엔 "저장하지 않고 다음 배치가 재시도"였다. 본문이 "ㅂㅈㄷ"이면 다음 배치도 같은 답을 받으므로
        // 그 전제는 영원히 성립하지 않았고, 운영에서 37건이 10분마다 LLM을 다시 불렀다
        every { summaryPort.findPendingReviews(any()) } returns listOf(pending(1, placeId = 10))
        every { llmPort.summarize(any()) } returns
            result(LlmSummaryResult.ReviewSummary(1, null, null), model = "gemini/flash")
        val saved = slot<List<NewReviewSummary>>()
        every { summaryPort.saveSummaries(capture(saved)) } returns Unit

        assertEquals(0, service.summarizePending(), "채운 요약은 0이지만")
        assertEquals(listOf(NewReviewSummary(1, null, null, "gemini/flash")), saved.captured, "둘 다 null인 행이 기록된다")
    }

    @Test
    fun `LLM이 빠뜨린 reviewId도 요약 불가로 기록한다 - 같은 매장을 다시 보내지 않게`() {
        every { summaryPort.findPendingReviews(any()) } returns
            listOf(pending(1, placeId = 10), pending(2, placeId = 10))
        every { llmPort.summarize(any()) } returns result(LlmSummaryResult.ReviewSummary(1, "좋아요", null))
        val saved = slot<List<NewReviewSummary>>()
        every { summaryPort.saveSummaries(capture(saved)) } returns Unit

        assertEquals(1, service.summarizePending())
        assertEquals(setOf(1L, 2L), saved.captured.map { it.reviewId }.toSet())
        assertEquals("좋아요", saved.captured.single { it.reviewId == 1L }.pros)
        assertEquals(null to null, saved.captured.single { it.reviewId == 2L }.let { it.pros to it.cons })
    }

    @Test
    fun `호출이 실패한 매장은 기록하지 않는다 - 그쪽은 다음 배치가 재시도해야 한다`() {
        every { summaryPort.findPendingReviews(any()) } returns listOf(pending(1, placeId = 10))
        every { llmPort.summarize(any()) } throws IllegalStateException("프로바이더 소진")

        assertEquals(0, service.summarizePending())
        verify(exactly = 0) { summaryPort.saveSummaries(any()) }
    }

    @Test
    fun `모델 표기가 저장에 실린다`() {
        every { summaryPort.findPendingReviews(any()) } returns listOf(pending(1, placeId = 10))
        every { llmPort.summarize(any()) } returns
            result(LlmSummaryResult.ReviewSummary(1, "좋아요", "아쉬워요"), model = "gemini/flash")
        val saved = slot<List<NewReviewSummary>>()
        every { summaryPort.saveSummaries(capture(saved)) } returns Unit

        service.summarizePending()

        assertEquals("gemini/flash", saved.captured.single().model)
    }

    @Test
    fun `미요약이 없으면 LLM을 부르지 않는다`() {
        every { summaryPort.findPendingReviews(any()) } returns emptyList()

        assertEquals(0, service.summarizePending())
        verify(exactly = 0) { llmPort.summarize(any()) }
    }
}
