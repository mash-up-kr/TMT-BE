package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ReviewMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val assetStore = InMemoryStore<MockAsset>(idPrefix = "asset")
    private val ticketLedger = MockTicketLedger()
    private val aiSummaryStore = MockAiSummaryStore()

    private val place = MockFixtures.place(placeStore, "델리스피자")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ReviewMockController(saveStore, placeStore, assetStore, ticketLedger, aiSummaryStore))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice(), MockReviewExceptionAdvice())
            .build()

    @Test
    fun `공개 리뷰 상세는 작성자와 isMine을 내린다 — 비로그인은 false`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 7, reviewId = "review_1")

        mockMvc
            .perform(get("/v1/reviews/review_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.author.userId").value("user_7"))
            .andExpect(jsonPath("$.author.nickname").value("미식가7"))
            .andExpect(jsonPath("$.rating").value(5))
            .andExpect(jsonPath("$.isMine").value(false))

        mockMvc
            .perform(get("/v1/reviews/review_1").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(jsonPath("$.isMine").value(true))
    }

    @Test
    fun `없거나 삭제된 리뷰는 REVIEW_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/reviews/review_999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `삭제하면 사진까지 완전 삭제되고 티켓 1장을 회수한다 (R6·R7)`() {
        assetStore.create { id -> MockAsset(id, ownerId = 1, contentType = "image/jpeg", attached = true) }
        MockFixtures.review(
            saveStore,
            place.placeId,
            ownerId = 1,
            reviewId = "review_1",
            photoAssetIds = listOf("asset_1"),
        )
        assertEquals(1, ticketLedger.availableCount(1))

        mockMvc
            .perform(delete("/v1/reviews/review_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNoContent)

        assertNull(saveStore.findById("save_1"))
        assertNull(assetStore.findById("asset_1"))
        assertEquals(0, ticketLedger.availableCount(1))
    }

    @Test
    fun `회수할 티켓이 없으면 409와 티켓 상태를 함께 내린다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 1, reviewId = "review_1")
        ticketLedger.tryConsume(1) // 잔고 0으로

        mockMvc
            .perform(delete("/v1/reviews/review_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REVIEW_DELETE_TICKET_REQUIRED"))
            .andExpect(jsonPath("$.title").value("리뷰를 삭제하려면 티켓 1장이 필요합니다."))
            .andExpect(jsonPath("$.ticket.requiredCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))
            .andExpect(jsonPath("$.ticket.shortageCount").value(1))
    }

    @Test
    fun `타인의 리뷰 삭제는 REVIEW_NOT_FOUND다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 7, reviewId = "review_1")

        mockMvc
            .perform(delete("/v1/reviews/review_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `요약이 생성되기 전에는 aiSummary가 null이다 (A2)`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 7, reviewId = "review_1")

        mockMvc
            .perform(get("/v1/reviews/review_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.aiSummary").doesNotExist())
    }

    @Test
    fun `요약이 생성되면 aiSummary가 채워진다`() {
        MockFixtures.review(saveStore, place.placeId, ownerId = 7, reviewId = "review_1")
        aiSummaryStore.put("review_1", pros = "분위기가 좋아요", cons = "웨이팅이 많아요")

        mockMvc
            .perform(get("/v1/reviews/review_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.aiSummary.pros").value("분위기가 좋아요"))
    }
}
