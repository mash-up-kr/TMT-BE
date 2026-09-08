package com.tmt.input.http.controller

import com.tmt.application.port.input.CheckTokenRevokedUseCase
import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.application.port.input.LogoutUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.JwtTokenCodec
import com.tmt.input.http.auth.TokenUse
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.PublicIds
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 카카오 로그인·토큰 (TMT-271·TMT-272) — 명세 v2 X. 로그인 성공 시 JWT를 발급하고,
 * 이후 요청은 `Authorization: Bearer {accessToken}`으로 인증한다. X-User-Id 스텁은 제거됐다.
 * 로그아웃(TMT-353)은 그 사용자의 refresh를 폐기해 재발급을 막는다 — access는 짧은 만료로 흘려보낸다.
 */
@Tag(name = "인증", description = "명세 v2 — X. 로그인·회원가입")
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val loginWithKakaoUseCase: LoginWithKakaoUseCase,
    private val tokenCodec: JwtTokenCodec,
    private val logoutUseCase: LogoutUseCase,
    private val checkTokenRevokedUseCase: CheckTokenRevokedUseCase,
) {
    @Operation(
        summary = "카카오 로그인",
        description =
            "카카오 인가 코드를 교환해 로그인하고 토큰을 발급한다. 처음 온 카카오 계정이면 users 행을 만들고 " +
                "`isNewUser=true`로 내린다.\n\n" +
                "`profileCompleted=false`면 가입 화면(`PUT /v1/users/me/profile`)을 끝내야 한다 — 그전까지 다른 API는 " +
                "SIGNUP_NOT_COMPLETED(403)로 막힌다. 이때 함께 내려가는 `nickname`은 카카오 닉네임이라 입력칸 " +
                "초깃값으로 쓸 수 있다. `profileImageUrl`은 가입 전에는 항상 null이다.\n\n" +
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
            userId = PublicIds.user(result.userId),
            nickname = result.nickname,
            profileImageUrl = result.profileImageUrl,
            isNewUser = result.isNewUser,
            profileCompleted = result.profileCompleted,
            accessToken = tokens.accessToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshToken = tokens.refreshToken,
        )
    }

    @Operation(
        summary = "토큰 재발급",
        description =
            "refresh 토큰으로 access·refresh 토큰을 새로 발급한다.\n\n" +
                "refresh까지 만료(AUTH_TOKEN_EXPIRED)거나 유효하지 않으면(AUTH_TOKEN_INVALID) 재로그인으로 분기한다. " +
                "로그아웃(`POST /v1/auth/logout`) 이전에 발급된 refresh도 AUTH_TOKEN_INVALID다 — 서명이 맞아도 거절한다.",
    )
    @ApiErrorCodes(ErrorCode.AUTH_TOKEN_INVALID, ErrorCode.AUTH_TOKEN_EXPIRED)
    @PostMapping("/token/refresh")
    fun refreshToken(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): TokenRefreshResponse {
        val claims = tokenCodec.parse(request.refreshToken, TokenUse.REFRESH)
        // 서명 검증 뒤에 한 번 더 본다 — 로그아웃 이전 발급분과 없는 사용자는 서명이 맞아도 재발급하지 않는다 (TMT-353)
        if (checkTokenRevokedUseCase.isRevoked(claims.userId, claims.issuedAt)) {
            throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
        }
        val tokens = tokenCodec.issue(claims.userId)
        return TokenRefreshResponse(
            accessToken = tokens.accessToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshToken = tokens.refreshToken,
        )
    }

    @Operation(
        summary = "로그아웃",
        description =
            "이 사용자에게 지금까지 발급된 refresh 토큰을 전부 폐기한다 (U8). 이후 그 refresh로 재발급하면 " +
                "AUTH_TOKEN_INVALID다. access 토큰은 만료(최대 1시간)까지 유효하다 — 클라이언트가 지운다.\n\n" +
                "멱등이다 — 이미 로그아웃했어도 204. 토큰이 없으면 401, 만료됐으면 401 AUTH_TOKEN_EXPIRED라 " +
                "다른 API와 같이 재발급 뒤 한 번 다시 부른다. 가입을 끝내지 않은 사용자도 부를 수 있다.",
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @UserId userId: Long,
    ) {
        logoutUseCase.logout(userId)
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
        @field:Schema(description = "사용자 ID 표기 (`user_7`). 다른 응답의 userId와 같은 형식이다", example = "user_7")
        val userId: String,
        val nickname: String,
        @field:Schema(nullable = true)
        val profileImageUrl: String?,
        @field:Schema(description = "이번 로그인으로 계정이 만들어졌는지")
        val isNewUser: Boolean,
        @field:Schema(description = "가입 화면(닉네임·프로필 사진)을 끝냈는지 — false면 가입 화면으로 보낸다")
        val profileCompleted: Boolean,
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
