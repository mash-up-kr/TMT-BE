package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class RecommendationMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val aiSummaryStore = MockAiSummaryStore()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(RecommendationMockController(saveStore, placeStore, aiSummaryStore))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `근거가 될 리뷰가 없으면 422다 (J 5-2)`() {
        MockFixtures.place(placeStore, "델리스피자")

        mockMvc
            .perform(post("/v1/recommendations/places").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_UNAVAILABLE"))
    }

    @Test
    fun `인증이 필수다`() {
        mockMvc.perform(post("/v1/recommendations/places")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `내가 리뷰한 매장은 추천하지 않고 요약은 그 매장 최신 리뷰의 것을 쓴다 (A3)`() {
        val mine = MockFixtures.place(placeStore, "델리스피자")
        val other = MockFixtures.place(placeStore, "오즈 커피")
        MockFixtures.review(saveStore, mine.placeId, ownerId = 1, reviewId = "review_1")
        MockFixtures.review(saveStore, other.placeId, ownerId = 999, reviewId = "review_2")
        aiSummaryStore.put("review_2", pros = "분위기가 좋아요", cons = "웨이팅이 길어요")

        mockMvc
            .perform(post("/v1/recommendations/places").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.place.placeId").value(other.placeId))
            .andExpect(jsonPath("$.summary.reviewId").value("review_2"))
            .andExpect(jsonPath("$.summary.pros").value("분위기가 좋아요"))
    }

    @Test
    fun `요약이 아직 없는 매장이면 summary가 null이다 (A2)`() {
        val mine = MockFixtures.place(placeStore, "델리스피자")
        val other = MockFixtures.place(placeStore, "오즈 커피")
        MockFixtures.review(saveStore, mine.placeId, ownerId = 1, reviewId = "review_1")
        MockFixtures.review(saveStore, other.placeId, ownerId = 999, reviewId = "review_2")

        mockMvc
            .perform(post("/v1/recommendations/places").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary").doesNotExist())
    }

    @Test
    fun `다시 누르면 다음 후보를 내린다 — 재추천 버튼이 동작해야 한다`() {
        val mine = MockFixtures.place(placeStore, "델리스피자")
        MockFixtures.place(placeStore, "오즈 커피")
        MockFixtures.place(placeStore, "서북면옥")
        MockFixtures.review(saveStore, mine.placeId, ownerId = 1, reviewId = "review_1")

        val first =
            mockMvc
                .perform(post("/v1/recommendations/places").header(UserIdArgumentResolver.HEADER, "1"))
                .andReturn()
                .response.contentAsString
        val second =
            mockMvc
                .perform(post("/v1/recommendations/places").header(UserIdArgumentResolver.HEADER, "1"))
                .andReturn()
                .response.contentAsString

        assert(first != second) { "같은 매장이 연달아 추천되면 재추천 버튼이 동작하지 않는다" }
    }
}
