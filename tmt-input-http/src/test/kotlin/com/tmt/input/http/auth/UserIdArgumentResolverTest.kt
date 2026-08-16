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
    fun `헤더가 있으면 사용자 ID로 해석한다`() {
        mockMvc
            .perform(get("/required").header(UserIdArgumentResolver.HEADER, "42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(42))
    }

    @Test
    fun `필수 파라미터인데 헤더가 없으면 401 UNAUTHORIZED다`() {
        mockMvc
            .perform(get("/required"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `선택 파라미터는 헤더가 없으면 null이다`() {
        mockMvc
            .perform(get("/optional"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").doesNotExist())
    }

    @Test
    fun `헤더가 숫자가 아니면 400 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/required").header(UserIdArgumentResolver.HEADER, "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }
}
