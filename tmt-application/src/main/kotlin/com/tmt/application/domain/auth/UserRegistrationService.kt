package com.tmt.application.domain.auth

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 신규 가입 처리 — 사용자 생성 후 가입 보상 티켓 1장을 발급한다 (T2, TMT-274에서 반영).
 *
 * 두 쓰기를 한 트랜잭션으로 묶지 않는다 — 생성의 kakao_id 유니크 충돌(동시 로그인 경쟁)을
 * null로 수습하는 경로가 트랜잭션 안에서는 rollback-only가 되어 로그인 자체가 실패한다.
 * 대신 보상 실패를 로그인 실패로 번지지 않게 격리하고 크게 남긴다 — 재발급 경로가 없는 유일한 창이다.
 */
@Service
class UserRegistrationService(
    private val userAccountPort: UserAccountPort,
    private val groupJoinTicketPort: GroupJoinTicketPort,
) {
    /** 같은 kakaoId가 이미 있으면(동시 로그인 경쟁) null — 호출자가 재조회한다 */
    fun register(
        kakaoId: Long,
        nickname: String,
        profileImageUrl: String?,
    ): UserAccount? {
        val created = userAccountPort.create(kakaoId, nickname, profileImageUrl) ?: return null
        runCatching { groupJoinTicketPort.grantForSignup(created.id) }
            .onFailure { logger.error(it) { "가입 보상 티켓 발급 실패 - userId=${created.id} 수동 보전 필요 (T2)" } }
        return created
    }
}
