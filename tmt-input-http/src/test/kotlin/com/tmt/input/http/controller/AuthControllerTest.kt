package com.tmt.input.http.controller

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.KakaoLoginResult
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.JwtTokenCodec
import com.tmt.input.http.auth.TokenUse
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Duration
import kotlin.test.assertEquals

class AuthControllerTest {
    private val useCase = StubLoginWithKakaoUseCase()
    private val tokenCodec =
        JwtTokenCodec("test-jwt-secret-that-is-32-bytes-long", Duration.ofHours(1), Duration.ofDays(30))

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(useCase, tokenCodec))
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `로그인 성공 응답에 사용자 정보와 토큰이 있다`() {
        val body =
            mockMvc
                .perform(login("""{"code":"auth-code","redirectUri":"http://localhost:3000/cb"}"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.nickname").value("준형이"))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600))
                .andReturn()
                .response.contentAsString

        assertEquals(
            KakaoLoginCommand(code = "auth-code", redirectUri = "http://localhost:3000/cb"),
            useCase.commands.single(),
        )
        // 발급된 access 토큰은 로그인한 사용자를 가리켜야 한다
        val accessToken = body.substringAfter("\"accessToken\":\"").substringBefore('"')
        assertEquals(7L, tokenCodec.parseUserId(accessToken, TokenUse.ACCESS))
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

    @Test
    fun `refresh 토큰으로 새 토큰 쌍을 발급한다`() {
        val refreshToken = tokenCodec.issue(7L).refreshToken

        val body =
            mockMvc
                .perform(refresh("""{"refreshToken":"$refreshToken"}"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600))
                .andReturn()
                .response.contentAsString

        val accessToken = body.substringAfter("\"accessToken\":\"").substringBefore('"')
        assertEquals(7L, tokenCodec.parseUserId(accessToken, TokenUse.ACCESS))
    }

    @Test
    fun `access 토큰으로는 재발급할 수 없다`() {
        val accessToken = tokenCodec.issue(7L).accessToken

        mockMvc
            .perform(refresh("""{"refreshToken":"$accessToken"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
    }

    @Test
    fun `만료된 refresh 토큰은 AUTH_TOKEN_EXPIRED다 - FE는 재로그인으로 분기한다`() {
        val expiredCodec =
            JwtTokenCodec("test-jwt-secret-that-is-32-bytes-long", Duration.ofHours(1), Duration.ofSeconds(-10))
        val expired = expiredCodec.issue(7L).refreshToken

        mockMvc
            .perform(refresh("""{"refreshToken":"$expired"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
    }

    private fun login(body: String) = post("/v1/auth/login/kakao").contentType(MediaType.APPLICATION_JSON).content(body)

    private fun refresh(body: String) =
        post("/v1/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content(body)

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
