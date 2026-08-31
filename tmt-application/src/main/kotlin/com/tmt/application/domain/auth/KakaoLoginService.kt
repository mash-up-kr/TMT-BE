package com.tmt.application.domain.auth

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.KakaoLoginResult
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.application.port.output.auth.KakaoAuthPort
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 카카오 로그인 (TMT-271) — 인가 코드로 프로필을 확보하고 kakao_id로 사용자를 찾거나 만든다.
 * 신규면 가입 처리([UserRegistrationService])를 거친다. 토큰 발급은 컨트롤러 계층 몫이다 (TMT-272).
 */
@Service
class KakaoLoginService(
    private val kakaoAuthPort: KakaoAuthPort,
    private val userAccountPort: UserAccountPort,
    private val userRegistrationService: UserRegistrationService,
) : LoginWithKakaoUseCase {
    override fun login(command: KakaoLoginCommand): KakaoLoginResult {
        val profile = kakaoAuthPort.fetchProfile(command.code, command.redirectUri)

        userAccountPort.findByKakaoId(profile.kakaoId)?.let { return it.toResult(isNewUser = false) }

        val created =
            userRegistrationService.register(
                kakaoId = profile.kakaoId,
                nickname = normalizeNickname(profile.nickname),
                profileImageUrl = profile.profileImageUrl,
            )
        if (created != null) return created.toResult(isNewUser = true)

        // 동시 로그인 경쟁에서 진 쪽 — 먼저 들어간 행을 읽는다. 그래도 없으면 우리 결함이다
        val existing =
            userAccountPort.findByKakaoId(profile.kakaoId)
                ?: run {
                    logger.error { "카카오 사용자 생성 경쟁 후 재조회 실패 - kakaoId=${profile.kakaoId}" }
                    throw TmtException(ErrorCode.INTERNAL_ERROR)
                }
        return existing.toResult(isNewUser = false)
    }

    /**
     * U3: 닉네임은 2~10자. 카카오 닉네임이 없거나 2자 미만이면 기본값, 10자를 넘으면 자른다 —
     * 확정 닉네임은 온보딩(TMT-273)에서 받는다.
     */
    private fun normalizeNickname(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.length < NICKNAME_MIN) return DEFAULT_NICKNAME
        return trimmed.take(NICKNAME_MAX)
    }

    private fun UserAccount.toResult(isNewUser: Boolean) =
        KakaoLoginResult(
            userId = id,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            isNewUser = isNewUser,
        )

    companion object {
        /** users_nickname_len CHECK (U3) */
        private const val NICKNAME_MIN = 2
        private const val NICKNAME_MAX = 10
        private const val DEFAULT_NICKNAME = "또맛또 미식가"
    }
}
