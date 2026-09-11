package com.tmt.input.http.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 어드민 판정 (TMT-421). 정본은 설정 `tmt.admin.user-ids`다 — **역할 컬럼이 아니다.**
 *
 * 이 레포에는 역할 개념이 없고(인증은 카카오 로그인 + JWT뿐), 칩 편집 하나를 위해
 * 역할 체계를 세우는 대신 목록 하나를 둔다. 대가는 **명단 변경이 배포**라는 것이고,
 * 어드민이 늘어 그게 불편해지면 그때 `users.role`로 옮긴다.
 *
 * 비어 있으면 **아무도 어드민이 아니다.** 설정 누락이 전원 허용으로 기울면 안 된다 —
 * 기본값이 열려 있는 쪽이 사고이고, 닫혀 있으면 403을 보고 설정을 채우면 된다.
 */
@Component
class AdminAllowlist(
    @Value("\${tmt.admin.user-ids:}") rawUserIds: String,
) {
    private val userIds: Set<Long> =
        rawUserIds
            .split(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toLongOrNull() }
            .toSet()

    init {
        if (userIds.isEmpty()) {
            // 운영에서 어드민 API가 전부 403일 때 여기부터 본다
            logger.warn { "어드민 허용 목록이 비어 있다 — tmt.admin.user-ids 미설정. 어드민 API는 전부 403이다" }
        } else {
            logger.info { "어드민 허용 목록 로드 - 인원=${userIds.size}" }
        }
    }

    fun isAdmin(userId: Long): Boolean = userId in userIds
}
