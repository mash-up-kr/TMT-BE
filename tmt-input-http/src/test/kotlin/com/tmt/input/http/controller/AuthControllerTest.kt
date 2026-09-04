package com.tmt.input.http.controller

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.KakaoLoginResult
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals

class AuthControllerTest {
    private val useCase = StubLoginWithKakaoUseCase()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(useCase))
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `로그인 성공 응답에 userId·isNewUser가 있다`() {
        mockMvc
            .perform(login("""{"code":"auth-code","redirectUri":"http://localhost:3000/cb"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(7))
            .andExpect(jsonPath("$.nickname").value("준형이"))
            .andExpect(jsonPath("$.isNewUser").value(true))

        assertEquals(
            KakaoLoginCommand(code = "auth-code", redirectUri = "http://localhost:3000/cb"),
            useCase.commands.single(),
        )
    }

    @Test
    fun `code가 비어 있으면 VALIDATION_FAILED다`() {
        mockMvc
            .perform(login("""{"code":"","redirectUri":"http://localhost:3000/cb"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertEquals(0, useCase.commands.size)
    }

    @Test
    fun `인가 코드가 거절되면 401 AUTH_KAKAO_CODE_INVALID다`() {
        useCase.error = TmtException(ErrorCode.AUTH_KAKAO_CODE_INVALID)

        mockMvc
            .perform(login("""{"code":"used","redirectUri":"http://localhost:3000/cb"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_KAKAO_CODE_INVALID"))
    }

    @Test
    fun `카카오 장애면 502 AUTH_KAKAO_UNAVAILABLE이다`() {
        useCase.error = TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)

        mockMvc
            .perform(login("""{"code":"c","redirectUri":"http://localhost:3000/cb"}"""))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("AUTH_KAKAO_UNAVAILABLE"))
    }

    private fun login(body: String) = post("/v1/auth/login/kakao").contentType(MediaType.APPLICATION_JSON).content(body)

    private class StubLoginWithKakaoUseCase : LoginWithKakaoUseCase {
        val commands = mutableListOf<KakaoLoginCommand>()
        var error: TmtException? = null
        var result =
            KakaoLoginResult(
                userId = 7L,
                nickname = "준형이",
                profileImageUrl = null,
                isNewUser = true,
            )

        override fun login(command: KakaoLoginCommand): KakaoLoginResult {
            commands += command
            error?.let { throw it }
            return result
        }
    }
}
