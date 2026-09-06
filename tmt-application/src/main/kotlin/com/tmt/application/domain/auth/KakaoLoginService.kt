package com.tmt.application.domain.auth

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.input.KakaoLoginResult
import com.tmt.application.port.input.LoginWithKakaoUseCase
import com.tmt.application.port.output.auth.KakaoAuthPort
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 카카오 로그인 (TMT-271) — 인가 코드로 프로필을 확보하고 kakao_id로 사용자를 찾거나 만든다.
 * 세션·토큰 발급은 TMT-272에서 붙는다.
 */
@Service
class KakaoLoginService(
    private val kakaoAuthPort: KakaoAuthPort,
    private val userAccountPort: UserAccountPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
) : LoginWithKakaoUseCase {
    override fun login(command: KakaoLoginCommand): KakaoLoginResult {
        val profile = kakaoAuthPort.fetchProfile(command.code, command.redirectUri)

        userAccountPort.findByKakaoId(profile.kakaoId)?.let { return it.toResult(isNewUser = false) }

        val created =
            userAccountPort.create(
                kakaoId = profile.kakaoId,
                nickname = normalizeNickname(profile.nickname),
                profileImageUrl = profile.profileImageUrl,
            )
        if (created != null) {
            // T2 — 가입 선물 1장. 근거의 source_id가 user_id라 두 번 불려도 UNIQUE가 막는다.
            // 카카오 호출이 앞에 있어 트랜잭션으로 묶지 않는다 — DB 커넥션을 외부 응답만큼 붙잡게 된다.
            // 그래서 생성과 발급 사이에서 죽으면 티켓 없는 계정이 남는데, 재발급이 안전하므로
            // 그때는 같은 근거로 다시 부르면 된다 (온보딩 TMT-273에서 이어붙일 자리다)
            groupJoinTicketPort.grantForSignup(created.id)
            return created.toResult(isNewUser = true)
        }

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
     * U3: 닉네임은 2~20자. 카카오 닉네임이 없거나 2자 미만이면 기본값, 20자를 넘으면 자른다 —
     * 확정 닉네임은 온보딩(TMT-273)에서 받는다.
     *
     * 길이는 코드포인트로 센다. `String.length`·`take`는 UTF-16 코드 유닛이라 이모지 닉네임을
     * 자르면 서로게이트 반쪽이 남아 DB 인코딩 오류가 난다 — CHECK(users_nickname_len)도
     * char_length라 코드포인트 기준이다.
     */
    private fun normalizeNickname(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        val codePoints = trimmed.codePoints().toArray()
        if (codePoints.size < NICKNAME_MIN) return DEFAULT_NICKNAME
        if (codePoints.size <= NICKNAME_MAX) return trimmed
        return String(codePoints, 0, NICKNAME_MAX)
    }

    private fun UserAccount.toResult(isNewUser: Boolean) =
        KakaoLoginResult(
            userId = id,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            isNewUser = isNewUser,
        )

    companion object {
        /** users_nickname_len CHECK (U3) — 상한은 V6에서 10자 → 20자로 늘었다 (TMT-350) */
        private const val NICKNAME_MIN = 2
        private const val NICKNAME_MAX = 20
        private const val DEFAULT_NICKNAME = "또맛또 미식가"
    }
}
