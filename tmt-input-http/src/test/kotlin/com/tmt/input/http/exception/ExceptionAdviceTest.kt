package com.tmt.input.http.exception

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 요청을 해석하지 못한 경우가 400으로 나가는지 본다 (TMT-343).
 *
 * Spring이 **컨트롤러에 들어가기 전에** 던지는 예외들이라 컨트롤러 테스트로는 잡히지 않았고,
 * 맨 아래 `Exception` 핸들러가 받아 운영에서 `?limit=abc` 하나에 500이 나가고 있었다.
 * 엔드포인트 전체에 걸리는 규칙이라 여기서 한 번만 검증한다.
 */
class ExceptionAdviceTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ProbeController())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `필수 쿼리 파라미터가 없으면 400이고 어느 파라미터인지 알려준다`() {
        mockMvc
            .perform(get("/probe/params"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.detail").value("size은(는) 필수입니다."))
    }

    @Test
    fun `숫자 자리에 문자가 오면 500이 아니라 400이다`() {
        mockMvc
            .perform(get("/probe/params").param("size", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.detail").value("size의 형식이 올바르지 않습니다."))
    }

    @Test
    fun `선택 파라미터의 형식이 틀려도 400이다`() {
        // 운영에서 `?limit=abc`가 500을 내던 자리다 — 선택이라고 해서 형식 오류가 면제되지 않는다
        mockMvc
            .perform(get("/probe/params").param("size", "1").param("limit", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `본문이 깨져 있으면 400이고 본문 조각을 응답에 싣지 않는다`() {
        mockMvc
            .perform(
                post("/probe/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name": "비밀값", """),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.detail").value("요청 본문을 읽을 수 없습니다."))
    }

    @Test
    fun `예상 못 한 예외는 그대로 500이다`() {
        // 400 확장이 서버 오류까지 삼키면 진짜 장애가 조용해진다
        mockMvc
            .perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
    }

    @Test
    fun `정상 요청은 그대로 통과한다`() {
        mockMvc
            .perform(get("/probe/params").param("size", "3"))
            .andExpect(status().isOk)
    }

    @RestController
    private class ProbeController {
        @GetMapping("/probe/params")
        fun params(
            @RequestParam size: Int,
            @RequestParam(required = false) limit: Int?,
        ) = mapOf("size" to size, "limit" to limit)

        @PostMapping("/probe/body")
        fun body(
            @RequestBody payload: Payload,
        ) = payload

        @GetMapping("/probe/boom")
        fun boom(): Nothing = throw IllegalStateException("예상 못 한 실패")

        data class Payload(
            val name: String,
        )
    }
}
