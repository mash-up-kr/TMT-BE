package com.tmt.input.http.controller

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.JwtTokenCodec
import com.tmt.input.http.auth.TokenUse
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
 * 카카오 로그인·토큰 (TMT-271·TMT-272) — 명세 v2 X. 로그인 성공 시 JWT를 발급하고,
 * 이후 요청은 `Authorization: Bearer {accessToken}`으로 인증한다. X-User-Id 스텁은 제거됐다.
 */
@Tag(name = "인증", description = "명세 v2 — X. 로그인·회원가입")
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val loginWithKakaoUseCase: LoginWithKakaoUseCase,
    private val tokenCodec: JwtTokenCodec,
) {
    @Operation(
        summary = "카카오 로그인",
        description =
            "카카오 인가 코드를 교환해 로그인하고 토큰을 발급한다. 처음 온 카카오 계정이면 users 행을 만들고 " +
                "`isNewUser=true`로 내린다 — FE는 이 값으로 온보딩(TMT-273)에 분기한다.\n\n" +
                "`redirectUri`는 인가 요청에 사용한 값과 같아야 한다. 이후 요청은 " +
                "`Authorization: Bearer {accessToken}`으로 보내고, 만료(AUTH_TOKEN_EXPIRED)되면 재발급 API로 갱신한다.",
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
        val tokens = tokenCodec.issue(result.userId)
        return KakaoLoginResponse(
            userId = result.userId,
            nickname = result.nickname,
            profileImageUrl = result.profileImageUrl,
            isNewUser = result.isNewUser,
            accessToken = tokens.accessToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshToken = tokens.refreshToken,
        )
    }

    @Operation(
        summary = "토큰 재발급",
        description =
            "refresh 토큰으로 access·refresh 토큰을 새로 발급한다.\n\n" +
                "refresh까지 만료(AUTH_TOKEN_EXPIRED)거나 유효하지 않으면(AUTH_TOKEN_INVALID) 재로그인으로 분기한다.",
    )
    @ApiErrorCodes(ErrorCode.AUTH_TOKEN_INVALID, ErrorCode.AUTH_TOKEN_EXPIRED)
    @PostMapping("/token/refresh")
    fun refreshToken(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): TokenRefreshResponse {
        // stateless라 서명 검증뿐이다 — 탈퇴·계정 차단이 생기면 여기서 사용자 존재·상태 확인을 추가해야 한다
        val userId = tokenCodec.parseUserId(request.refreshToken, TokenUse.REFRESH)
        val tokens = tokenCodec.issue(userId)
        return TokenRefreshResponse(
            accessToken = tokens.accessToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshToken = tokens.refreshToken,
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
        val accessToken: String,
        @field:Schema(description = "accessToken 만료까지 남은 초")
        val accessTokenExpiresIn: Long,
        val refreshToken: String,
    )

    data class TokenRefreshRequest(
        @field:NotBlank
        val refreshToken: String,
    )

    data class TokenRefreshResponse(
        val accessToken: String,
        @field:Schema(description = "accessToken 만료까지 남은 초")
        val accessTokenExpiresIn: Long,
        val refreshToken: String,
    )
}
