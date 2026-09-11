package com.tmt.input.http.controller

import com.tmt.application.port.input.GetUserRankingsUseCase
import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.input.UserRankingView
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.application.port.input.UserRankingsResult
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** 유저 랭킹 어댑터 계약 — ID 표기·커서 왕복·비로그인 열람을 지킨다 (TMT-436). */
class RankingControllerTest {
    private var lastRequest: UserRankingsRequest? = null
    private var result = UserRankingsResult(items = emptyList(), hasNext = false)

    private val getUserRankings =
        GetUserRankingsUseCase { request ->
            lastRequest = request
            result
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(RankingController(getUserRankings))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `비로그인도 접두 ID 표기로 목록을 받는다`() {
        result = UserRankingsResult(items = listOf(view(userId = 7)), hasNext = false)

        mockMvc
            .perform(get("/v1/rankings/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].userId").value("user_7"))
            .andExpect(jsonPath("$.items[0].nickname").value("유저7"))
            .andExpect(jsonPath("$.items[0].reviewCount").value(3))
            .andExpect(jsonPath("$.items[0].memberCount").value(12))
            .andExpect(jsonPath("$.items[0].profileImageUrl").doesNotExist())
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `limit은 규약 상한으로 잘린다`() {
        mockMvc.perform(get("/v1/rankings/users").param("limit", "999")).andExpect(status().isOk)

        assertEquals(50, requireNotNull(lastRequest).limit)
    }

    @Test
    fun `다음 페이지가 있으면 커서를 발급하고 그대로 되읽는다`() {
        result =
            UserRankingsResult(
                items = listOf(view(userId = 7)),
                hasNext = true,
                lastKey = UserRankingKey(reviewCount = 3, userId = 7),
            )

        val body =
            mockMvc
                .perform(get("/v1/rankings/users"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc
            .perform(get("/v1/rankings/users").param("cursor", cursor))
            .andExpect(status().isOk)

        val after = requireNotNull(requireNotNull(lastRequest).after)
        assertEquals(3, after.reviewCount)
        assertEquals(7L, after.userId)
    }

    @Test
    fun `해석할 수 없는 커서는 INVALID_CURSOR다`() {
        mockMvc
            .perform(get("/v1/rankings/users").param("cursor", "not-a-cursor"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private fun view(userId: Long) =
        UserRankingView(
            userId = userId,
            nickname = "유저$userId",
            profileImageUrl = null,
            reviewCount = 3,
            memberCount = 12,
        )
}
