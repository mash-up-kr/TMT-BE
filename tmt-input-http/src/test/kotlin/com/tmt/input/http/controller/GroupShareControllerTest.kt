package com.tmt.input.http.controller

import com.tmt.application.port.input.GetReviewSharesUseCase
import com.tmt.application.port.input.ReplaceReviewSharesUseCase
import com.tmt.application.port.input.ReplaceSharesResult
import com.tmt.application.port.input.ReviewShareItemView
import com.tmt.application.port.input.ReviewSharesRequest
import com.tmt.application.port.input.ReviewSharesResult
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/** 리뷰 공유 집합 실구현의 어댑터 계약 — 응답 형태·ID 표기·커서가 mock과 같은지 지킨다. */
class GroupShareControllerTest {
    private var lastListRequest: ReviewSharesRequest? = null
    private var lastReplace: Triple<Long, Long, List<Long>>? = null
    private var listResult = ReviewSharesResult(items = emptyList(), sharedCount = 0, hasNext = false)
    private var replaceResult = ReplaceSharesResult(sharedReviewIds = emptyList(), sharedCount = 0)

    private val listUseCase =
        GetReviewSharesUseCase { request ->
            lastListRequest = request
            listResult
        }
    private val replaceUseCase =
        ReplaceReviewSharesUseCase { groupId, userId, reviewIds ->
            lastReplace = Triple(groupId, userId, reviewIds)
            replaceResult
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(GroupShareController(listUseCase, replaceUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `공유 목록이 mock과 같은 형태로 나간다 — rv_ 접두·sharedCount`() {
        listResult = ReviewSharesResult(items = listOf(item()), sharedCount = 2, hasNext = false)

        mockMvc
            .perform(get("/v1/groups/group_1/review-shares").header("X-User-Id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_9"))
            .andExpect(jsonPath("$.items[0].isShared").value(true))
            .andExpect(jsonPath("$.sharedCount").value(2))
    }

    @Test
    fun `비로그인이면 401이다`() {
        mockMvc
            .perform(get("/v1/groups/group_1/review-shares"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT은 rv_ 접두를 풀어 넘기고 응답도 접두로 돌려준다`() {
        replaceResult = ReplaceSharesResult(sharedReviewIds = listOf(1L, 5L), sharedCount = 2)

        mockMvc
            .perform(
                put("/v1/groups/group_1/review-shares")
                    .header("X-User-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"reviewIds":["rv_1","rv_5"]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.groupId").value("group_1"))
            .andExpect(jsonPath("$.sharedReviewIds[0]").value("rv_1"))
            .andExpect(jsonPath("$.sharedCount").value(2))

        assertEquals(Triple(1L, 1L, listOf(1L, 5L)), lastReplace)
    }

    @Test
    fun `형식이 어긋난 reviewId는 REVIEW_NOT_FOUND다`() {
        mockMvc
            .perform(
                put("/v1/groups/group_1/review-shares")
                    .header("X-User-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"reviewIds":["review_1"]}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
    }

    @Test
    fun `다음 페이지가 있으면 커서를 발급하고 같은 조건에서 되읽는다`() {
        listResult = ReviewSharesResult(items = listOf(item()), sharedCount = 1, hasNext = true)

        val body =
            mockMvc
                .perform(get("/v1/groups/group_1/review-shares").header("X-User-Id", "1"))
                .andReturn()
                .response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc
            .perform(get("/v1/groups/group_1/review-shares").header("X-User-Id", "1").param("cursor", cursor))
            .andExpect(status().isOk)
        assertEquals(9L, requireNotNull(requireNotNull(lastListRequest).after).reviewId)

        // 다른 사용자가 쓰면 무효 — 목록이 조회자 것이다
        mockMvc
            .perform(get("/v1/groups/group_1/review-shares").header("X-User-Id", "2").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private fun item() =
        ReviewShareItemView(
            reviewId = 9L,
            placeName = "가게",
            thumbnailUrl = "https://m/t.jpg",
            contentPreview = "본문",
            isShared = true,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        )
}
