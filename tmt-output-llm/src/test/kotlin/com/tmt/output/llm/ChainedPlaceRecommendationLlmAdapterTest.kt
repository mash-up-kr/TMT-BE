package com.tmt.output.llm

import com.tmt.application.port.output.llm.PlaceChoiceRequest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 매장 추천 프로바이더 체인 (TMT-289). 후보 밖 id를 걸러내는 것과 재추천이 굳지 않게
 * 흔드는 것이 여기 로직이라 실제 LLM 없이 검증한다.
 */
class ChainedPlaceRecommendationLlmAdapterTest {
    private fun client(
        label: String,
        enabled: Boolean = true,
        behavior: (String, Double) -> String,
    ) = object : ChatJsonClient {
        override val modelLabel = label
        override val enabled = enabled

        override fun completeJson(
            systemPrompt: String,
            userPrompt: String,
            temperature: Double,
        ): String = behavior(userPrompt, temperature)
    }

    private fun request(vararg placeIds: Long) =
        PlaceChoiceRequest(
            seeds = listOf(PlaceChoiceRequest.Seed("한판승부", "cat_meat", 5, "고기가 두껍다")),
            candidates =
                placeIds.map {
                    PlaceChoiceRequest.Candidate(it, "후보$it", "cat_meat", "은평구 갈현동", 4.2)
                },
        )

    @Test
    fun `후보 안의 placeId를 고르면 그대로 돌려준다`() {
        val adapter = ChainedPlaceRecommendationLlmAdapter(listOf(client("groq/x") { _, _ -> """{"placeId":9}""" }))

        assertEquals(9L, adapter.choose(request(7L, 9L)))
    }

    @Test
    fun `후보 밖 placeId면 다음 프로바이더로 넘어간다`() {
        // 삼키고 첫 후보로 때우면 "추천했다"는 사실만 남고 근거가 사라진다
        var geminiCalled = false
        val adapter =
            ChainedPlaceRecommendationLlmAdapter(
                listOf(
                    client("groq/x") { _, _ -> """{"placeId":404}""" },
                    client("gemini/y") { _, _ ->
                        geminiCalled = true
                        """{"placeId":7}"""
                    },
                ),
            )

        assertEquals(7L, adapter.choose(request(7L, 9L)))
        assertTrue(geminiCalled)
    }

    @Test
    fun `전부 후보 밖을 주면 예외다`() {
        // 서비스가 RECOMMENDATION_FAILED로 바꾼다
        val adapter =
            ChainedPlaceRecommendationLlmAdapter(
                listOf(
                    client("groq/x") { _, _ -> """{"placeId":404}""" },
                    client("gemini/y") { _, _ -> """{"placeId":405}""" },
                ),
            )

        assertFailsWith<IllegalStateException> { adapter.choose(request(7L, 9L)) }
    }

    @Test
    fun `활성 프로바이더가 없으면 예외다`() {
        val adapter =
            ChainedPlaceRecommendationLlmAdapter(
                listOf(client("groq/x", enabled = false) { _, _ -> """{"placeId":7}""" }),
            )

        assertFailsWith<IllegalStateException> { adapter.choose(request(7L, 9L)) }
    }

    @Test
    fun `추천은 요약보다 높은 temperature로 부른다`() {
        // 같은 재료로도 결과가 달라져야 재추천 버튼이 동작한다
        var used = 0.0
        val adapter =
            ChainedPlaceRecommendationLlmAdapter(
                listOf(
                    client("groq/x") { _, temperature ->
                        used = temperature
                        """{"placeId":7}"""
                    },
                ),
            )

        adapter.choose(request(7L, 9L))

        assertEquals(ChatJsonClient.CHOICE_TEMPERATURE, used)
        assertTrue(used > ChatJsonClient.SUMMARY_TEMPERATURE)
    }

    @Test
    fun `후보 순서를 섞어 보내 프롬프트가 매번 같지 않다`() {
        // 후보 30개를 고르는 SQL이 결정적이라, 순서까지 그대로면 프롬프트가 완전히 같아진다
        val prompts = mutableSetOf<String>()
        val adapter =
            ChainedPlaceRecommendationLlmAdapter(
                listOf(
                    client("groq/x") { prompt, _ ->
                        prompts += prompt
                        """{"placeId":1}"""
                    },
                ),
            )
        val manyCandidates = request(*(1L..12L).toList().toLongArray())

        repeat(20) { adapter.choose(manyCandidates) }

        assertTrue(prompts.size > 1, "후보 순서가 고정돼 프롬프트가 늘 같았다")
    }

    @Test
    fun `후보가 섞여도 고른 id 검증은 그대로다`() {
        val adapter = ChainedPlaceRecommendationLlmAdapter(listOf(client("groq/x") { _, _ -> """{"placeId":12}""" }))

        assertEquals(12L, adapter.choose(request(*(1L..12L).toList().toLongArray())))
    }
}
