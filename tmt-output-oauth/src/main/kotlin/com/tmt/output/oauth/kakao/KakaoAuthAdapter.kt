package com.tmt.output.oauth.kakao

import com.tmt.application.port.output.auth.KakaoAuthPort
import com.tmt.application.port.output.auth.KakaoProfile
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 카카오 OAuth 어댑터 (TMT-271) — 인가 코드를 토큰으로 교환하고 `/v2/user/me`로 프로필을 읽는다.
 *
 * 실패 구분: 사용자가 다시 로그인하면 되는 실패(코드 만료·재사용)만 [ErrorCode.AUTH_KAKAO_CODE_INVALID]로,
 * 나머지(앱 설정 오류·카카오 장애)는 전부 [ErrorCode.AUTH_KAKAO_UNAVAILABLE]로 낸다 —
 * 설정 오류를 사용자 실패로 내면 원인이 로그 없이 묻힌다.
 */
@Component
class KakaoAuthAdapter(
    private val httpClient: KakaoHttpClient,
    @param:Value("\${tmt.auth.kakao.rest-api-key:}") private val restApiKey: String,
    @param:Value("\${tmt.auth.kakao.client-secret:}") private val clientSecret: String,
) : KakaoAuthPort {
    override fun fetchProfile(
        code: String,
        redirectUri: String,
    ): KakaoProfile {
        if (restApiKey.isBlank() || clientSecret.isBlank()) {
            logger.error { "카카오 키가 없다 - tmt.auth.kakao.rest-api-key·client-secret 설정 확인" }
            throw TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
        }
        return fetchUser(exchangeToken(code, redirectUri))
    }

    private fun exchangeToken(
        code: String,
        redirectUri: String,
    ): String {
        val response =
            runCatching {
                httpClient.postForm(
                    TOKEN_URL,
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to restApiKey,
                        "client_secret" to clientSecret,
                        "redirect_uri" to redirectUri,
                        "code" to code,
                    ),
                )
            }.getOrElse { throw tokenFailure(it) }

        return response.path("access_token").asString().orEmpty().ifBlank {
            logger.error { "카카오 토큰 응답에 access_token이 없다" }
            throw TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
        }
    }

    /**
     * `invalid_grant`(만료·재사용 코드, KOE320)만 사용자 재시도 대상이다. 그 외 4xx는
     * 리다이렉트 URI 불일치(KOE303)·앱 설정 오류처럼 우리가 고쳐야 하는 문제라 502로 끊고 크게 남긴다.
     */
    private fun tokenFailure(e: Throwable): TmtException {
        if (e !is KakaoHttpStatusException) {
            logger.warn(e) { "카카오 토큰 교환 호출 실패" }
            return TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
        }
        val error =
            e.body
                ?.path("error")
                ?.asString()
                .orEmpty()
        val errorCode =
            e.body
                ?.path("error_code")
                ?.asString()
                .orEmpty()
        if (error == INVALID_GRANT) {
            logger.warn { "카카오 인가 코드 거절 - error_code=$errorCode" }
            return TmtException(ErrorCode.AUTH_KAKAO_CODE_INVALID)
        }
        logger.error { "카카오 토큰 교환 거절 - status=${e.status}, error=$error, error_code=$errorCode" }
        return TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
    }

    private fun fetchUser(accessToken: String): KakaoProfile {
        val response =
            runCatching { httpClient.getWithBearer(USER_ME_URL, accessToken) }
                .getOrElse {
                    logger.warn(it) { "카카오 사용자 조회 실패" }
                    throw TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
                }

        val kakaoId = response.path("id").asLong(0L)
        if (kakaoId <= 0L) {
            logger.error { "카카오 사용자 응답에 id가 없다" }
            throw TmtException(ErrorCode.AUTH_KAKAO_UNAVAILABLE)
        }
        val profile = response.path("kakao_account").path("profile")
        return KakaoProfile(
            kakaoId = kakaoId,
            nickname = profile.text("nickname"),
            profileImageUrl = profile.text("profile_image_url"),
        )
    }

    private fun tools.jackson.databind.JsonNode.text(field: String): String? =
        path(field)
            .asString()
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }

    companion object {
        const val TOKEN_URL = "https://kauth.kakao.com/oauth/token"
        const val USER_ME_URL = "https://kapi.kakao.com/v2/user/me"
        private const val INVALID_GRANT = "invalid_grant"
    }
}
