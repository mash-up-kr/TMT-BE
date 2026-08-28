package com.tmt.output.llm

import com.tmt.application.port.output.llm.PlaceReviewsToSummarize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChainedReviewSummaryLlmAdapterTest {
    private val request =
        PlaceReviewsToSummarize(
            placeName = "우래옥",
            reviews = listOf(PlaceReviewsToSummarize.ReviewText(1, 5, "평냉이 최고")),
        )

    private fun client(
        label: String,
        enabled: Boolean = true,
        behavior: () -> String,
    ) = object : ChatJsonClient {
        override val modelLabel = label
        override val enabled = enabled

        override fun completeJson(
            systemPrompt: String,
            userPrompt: String,
        ): String = behavior()
    }

    private val ok = """{"summaries":[{"reviewId":1,"pros":"면이 좋아요","cons":""}]}"""

    @Test
    fun `1순위가 성공하면 2순위를 부르지 않는다`() {
        var geminiCalled = false
        val adapter =
            ChainedReviewSummaryLlmAdapter(
                listOf(
                    client("groq/x") { ok },
                    client("gemini/y") {
                        geminiCalled = true
                        ok
                    },
                ),
            )

        val result = adapter.summarize(request)

        assertEquals("groq/x", result.model)
        assertEquals(false, geminiCalled)
    }

    @Test
    fun `1순위가 실패하면 2순위로 넘어간다`() {
        val adapter =
            ChainedReviewSummaryLlmAdapter(
                listOf(
                    client("groq/x") { error("429") },
                    client("gemini/y") { ok },
                ),
            )

        assertEquals("gemini/y", adapter.summarize(request).model)
    }

    @Test
    fun `키 없는 프로바이더는 건너뛴다`() {
        val adapter =
            ChainedReviewSummaryLlmAdapter(
                listOf(
                    client("groq/x", enabled = false) { error("호출되면 안 된다") },
                    client("gemini/y") { ok },
                ),
            )

        assertEquals("gemini/y", adapter.summarize(request).model)
    }

    @Test
    fun `전부 실패하면 예외다 - 배치가 매장 단위로 삼키고 재시도한다`() {
        val adapter = ChainedReviewSummaryLlmAdapter(listOf(client("groq/x") { error("boom") }))

        assertFailsWith<IllegalStateException> { adapter.summarize(request) }
    }

    @Test
    fun `빈 문자열 pros·cons는 null로 정규화한다`() {
        val adapter = ChainedReviewSummaryLlmAdapter(listOf(client("groq/x") { ok }))

        val summary = adapter.summarize(request).summaries.single()

        assertEquals("면이 좋아요", summary.pros)
        assertNull(summary.cons)
    }
}
