package com.tmt.input.http.controller

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.config.ApiErrorCodes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 카카오 로그인 (TMT-271) — 명세 v2 X. 세션·토큰 발급(TMT-272) 전까지의 과도기 계약:
 * FE는 응답의 `userId`를 기존 `X-User-Id` 스텁 헤더에 그대로 쓴다.
 */
@Tag(name = "인증", description = "명세 v2 — X. 로그인·회원가입")
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val loginWithKakaoUseCase: LoginWithKakaoUseCase,
) {
    @Operation(
        summary = "카카오 로그인",
        description =
            "카카오 인가 코드를 교환해 로그인한다. 처음 온 카카오 계정이면 users 행을 만들고 " +
                "`isNewUser=true`로 내린다 — FE는 이 값으로 온보딩(TMT-273)에 분기한다.\n\n" +
                "`redirectUri`는 인가 요청에 사용한 값과 같아야 한다. 토큰 발급은 TMT-272에서 추가되며, " +
                "그 전까지는 응답의 `userId`를 `X-User-Id` 헤더로 사용한다.",
    )
    @ApiErrorCodes(ErrorCode.AUTH_KAKAO_CODE_INVALID, ErrorCode.AUTH_KAKAO_UNAVAILABLE)
    @PostMapping("/login/kakao")
    fun loginWithKakao(
        @Valid @RequestBody request: KakaoLoginRequest,
    ): KakaoLoginResponse {
        val result =
            loginWithKakaoUseCase.login(
                KakaoLoginCommand(code = request.code, redirectUri = request.redirectUri),
            )
        return KakaoLoginResponse(
            userId = result.userId,
            nickname = result.nickname,
            profileImageUrl = result.profileImageUrl,
            isNewUser = result.isNewUser,
        )
    }

    data class KakaoLoginRequest(
        @field:NotBlank
        @field:Schema(description = "카카오 인가 코드. 1회용이라 재사용하면 AUTH_KAKAO_CODE_INVALID다")
        val code: String,
        @field:NotBlank
        @field:Schema(
            description = "인가 요청에 사용한 리다이렉트 URI",
            example = "http://localhost:3000/auth/kakao/callback",
        )
        val redirectUri: String,
    )

    data class KakaoLoginResponse(
        val userId: Long,
        val nickname: String,
        @field:Schema(nullable = true)
        val profileImageUrl: String?,
        @field:Schema(description = "이번 로그인으로 계정이 만들어졌는지 — 온보딩 분기 기준")
        val isNewUser: Boolean,
    )
}
