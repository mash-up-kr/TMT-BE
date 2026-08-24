package com.tmt.input.http.idempotency

import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

class IdempotencyKeyArgumentResolverTest {
    @RestController
    private class FixtureController {
        @PostMapping("/required")
        fun required(
            @IdempotencyKey key: String,
        ) = mapOf("key" to key)

        @PostMapping("/optional")
        fun optional(
            @IdempotencyKey key: String?,
        ) = mapOf("key" to key)
    }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(FixtureController())
            .setCustomArgumentResolvers(IdempotencyKeyArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `헤더가 있으면 앞뒤 공백을 떼고 넘긴다`() {
        mockMvc
            .perform(post("/required").header(IdempotencyKeyArgumentResolver.HEADER, "  key-1  "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("key-1"))
    }

    @Test
    fun `필수인데 헤더가 없으면 400 VALIDATION_FAILED다`() {
        mockMvc
            .perform(post("/required"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `빈 문자열은 헤더가 없는 것과 같이 본다`() {
        mockMvc
            .perform(post("/required").header(IdempotencyKeyArgumentResolver.HEADER, "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `선택 파라미터는 헤더가 없으면 null이다`() {
        mockMvc
            .perform(post("/optional"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").doesNotExist())
    }

    @Test
    fun `컬럼 길이를 넘는 키는 400 VALIDATION_FAILED다`() {
        val tooLong = "k".repeat(IdempotencyRecord.IDEM_KEY_MAX_LENGTH + 1)

        mockMvc
            .perform(post("/required").header(IdempotencyKeyArgumentResolver.HEADER, tooLong))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }
}
