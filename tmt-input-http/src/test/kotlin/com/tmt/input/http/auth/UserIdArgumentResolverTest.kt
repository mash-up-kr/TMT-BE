package com.tmt.input.http.auth

import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class UserIdArgumentResolverTest {
    @RestController
    private class FixtureController {
        @GetMapping("/required")
        fun required(
            @UserId userId: Long,
        ) = mapOf("userId" to userId)

        @GetMapping("/optional")
        fun optional(
            @UserId userId: Long?,
        ) = mapOf("userId" to userId)
    }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(FixtureController())
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `인증 필터가 실은 요청 속성을 사용자 ID로 해석한다`() {
        mockMvc
            .perform(get("/required").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 42L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(42))
    }

    @Test
    fun `필수 파라미터인데 인증 주체가 없으면 401 UNAUTHORIZED다`() {
        mockMvc
            .perform(get("/required"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `선택 파라미터는 인증 주체가 없으면 null이다`() {
        mockMvc
            .perform(get("/optional"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").doesNotExist())
    }

    @Test
    fun `X-User-Id 헤더는 더 이상 해석하지 않는다`() {
        mockMvc
            .perform(get("/required").header("X-User-Id", "42"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }
}
