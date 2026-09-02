package com.tmt.input.http.controller

import com.tmt.application.port.input.DeleteReviewUseCase
import com.tmt.application.port.input.GetReviewDetailUseCase
import com.tmt.application.port.input.ReviewDetailView
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/**
 * 리뷰 상세·삭제 실구현의 어댑터 계약 — 응답 형태가 mock과 같은지, 티켓 409에 티켓 상태가
 * 실리는지(규약 §3-2)를 지킨다.
 */
class ReviewControllerTest {
    private var availableTickets = 1
    private val deleted = mutableListOf<Pair<Long, Long>>()

    private val detailUseCase =
        object : GetReviewDetailUseCase {
            override fun get(
                viewerId: Long?,
                reviewId: Long,
            ): ReviewDetailView {
                if (reviewId != 1L) throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
                return ReviewDetailView(
                    reviewId = 1,
                    author = ReviewDetailView.Author(userId = 7, nickname = "하아얀", profileImageUrl = null),
                    place =
                        ReviewDetailView.Place(
                            placeId = 9,
                            name = "델리스피자",
                            roadAddress = "서울 마포구 도화동 200-14",
                            categoryName = "양식",
                        ),
                    photos =
                        listOf(
                            ReviewDetailView.Photo(photoId = 3, url = "https://cdn.example.com/1.jpg", order = 0),
                        ),
                    tags = listOf(ReviewDetailView.Tag("tag_couple", "연인")),
                    rating = 5,
                    content = "맛도 있고 분위기도 좋아요.",
                    aiSummary = null,
                    isMine = viewerId == 7L,
                    createdAt = Instant.parse("2026-08-12T09:11:03.412Z"),
                )
            }
        }

    private val deleteUseCase =
        object : DeleteReviewUseCase {
            override fun delete(
                userId: Long,
                reviewId: Long,
            ) {
                if (reviewId != 1L || userId != 7L) throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
                if (availableTickets <= 0) {
                    throw TicketShortageException(ErrorCode.REVIEW_DELETE_TICKET_REQUIRED, availableCount = 0)
                }
                availableTickets -= 1
                deleted += userId to reviewId
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ReviewController(detailUseCase, deleteUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `공개 리뷰 상세는 작성자와 isMine을 내린다 — 비로그인은 false`() {
        mockMvc
            .perform(get("/v1/reviews/rv_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("rv_1"))
            .andExpect(jsonPath("$.author.userId").value("user_7"))
            .andExpect(jsonPath("$.author.nickname").value("하아얀"))
            .andExpect(jsonPath("$.place.placeId").value("place_9"))
            .andExpect(jsonPath("$.photos[0].photoId").value("sp_3"))
            .andExpect(jsonPath("$.tags[0].tagId").value("tag_couple"))
            .andExpect(jsonPath("$.rating").value(5))
            .andExpect(jsonPath("$.aiSummary").doesNotExist())
            .andExpect(jsonPath("$.createdAt").value("2026-08-12T09:11:03.412Z"))
            .andExpect(jsonPath("$.isMine").value(false))

        mockMvc
            .perform(get("/v1/reviews/rv_1").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(jsonPath("$.isMine").value(true))
    }

    @Test
    fun `없는 리뷰와 형식이 어긋난 id는 REVIEW_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/reviews/rv_999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))

        mockMvc
            .perform(get("/v1/reviews/nope"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `삭제는 204다`() {
        mockMvc
            .perform(delete("/v1/reviews/rv_1").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isNoContent)

        assertEquals(listOf(7L to 1L), deleted)
    }

    @Test
    fun `회수할 티켓이 없으면 409와 티켓 상태를 함께 내린다`() {
        availableTickets = 0

        mockMvc
            .perform(delete("/v1/reviews/rv_1").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REVIEW_DELETE_TICKET_REQUIRED"))
            .andExpect(jsonPath("$.title").value("리뷰를 삭제하려면 티켓 1장이 필요합니다."))
            .andExpect(jsonPath("$.ticket.requiredCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))
            .andExpect(jsonPath("$.ticket.shortageCount").value(1))
    }

    @Test
    fun `타인의 리뷰 삭제도 REVIEW_NOT_FOUND다 — 존재 여부를 감춘다`() {
        mockMvc
            .perform(delete("/v1/reviews/rv_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `삭제는 로그인이 필요하다`() {
        mockMvc
            .perform(delete("/v1/reviews/rv_1"))
            .andExpect(status().isUnauthorized)
    }
}
