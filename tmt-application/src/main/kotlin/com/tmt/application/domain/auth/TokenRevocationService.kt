package com.tmt.application.domain.auth

import com.tmt.application.port.input.CheckRefreshAllowedUseCase
import com.tmt.application.port.input.LogoutUseCase
import com.tmt.application.port.output.persistence.UserAccountPort
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 로그아웃과 refresh 폐기 판정 (TMT-353, U8).
 *
 * 토큰은 stateless라 개별 토큰을 저장하지 않는다. 대신 사용자 행에 "이 시각 전 발급분은 무효"를
 * 한 번 찍고([UserAccountPort.invalidateTokensIssuedBefore]), 재발급이 refresh의 발급 시각을 그 값과
 * 비교한다. 그래서 로그아웃은 **이 사용자의 전 기기** 로그아웃이다 — 모바일 웹 하나를 쓰는 지금은
 * 그게 자연스럽고, 기기별이 필요해지면 그때 토큰 저장소를 붙인다.
 *
 * access는 여기서 보지 않는다 — 요청마다 DB를 읽어야 해서, 짧은 만료(1h)로 흘려보낸다 (X 명세 §4-2).
 */
@Service
class TokenRevocationService(
    private val userAccountPort: UserAccountPort,
) : LogoutUseCase,
    CheckRefreshAllowedUseCase {
    override fun logout(userId: Long) {
        // 결과를 보지 않는다 — 사용자가 없거나 이미 로그아웃했어도 "폐기할 것이 없다"일 뿐, 실패가 아니다 (멱등)
        userAccountPort.invalidateTokensIssuedBefore(userId, Instant.now())
    }

    override fun isRefreshAllowed(
        userId: Long,
        issuedAt: Instant,
    ): Boolean {
        // 사용자가 없으면 발급해 줄 대상도 없다 — 서명만 보던 재발급에 존재 확인이 여기서 붙는다
        val account = userAccountPort.findById(userId) ?: return false
        val cutoff = account.tokensInvalidBefore ?: return true
        // JWT iat는 초 단위라 초 아래를 버리고 비교한다. 로그아웃과 같은 초에 발급된 토큰은 살려둔다 —
        // 로그아웃 직후 재로그인이 자기 토큰을 스스로 거절하는 쪽이 더 나쁘다
        return !issuedAt.isBefore(cutoff.truncatedTo(ChronoUnit.SECONDS))
    }
}
