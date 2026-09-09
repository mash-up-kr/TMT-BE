package com.tmt.application.domain.auth

import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenRevocationServiceTest {
    private val userPort = FakeUserAccountPort()
    private val service = TokenRevocationService(userPort)

    @Test
    fun `로그아웃한 적이 없으면 어떤 발급 시각도 거절하지 않는다`() {
        userPort.put(account(7L, tokensInvalidBefore = null))

        assertTrue(service.isRefreshAllowed(7L, Instant.parse("2026-09-08T10:00:00Z")))
    }

    @Test
    fun `로그아웃 이전에 발급된 refresh는 거절한다`() {
        userPort.put(account(7L, tokensInvalidBefore = Instant.parse("2026-09-08T10:00:00.400Z")))

        assertFalse(service.isRefreshAllowed(7L, Instant.parse("2026-09-08T09:59:59Z")))
    }

    @Test
    fun `로그아웃 이후에 발급된 refresh는 받아준다`() {
        userPort.put(account(7L, tokensInvalidBefore = Instant.parse("2026-09-08T10:00:00.400Z")))

        assertTrue(service.isRefreshAllowed(7L, Instant.parse("2026-09-08T10:00:01Z")))
    }

    @Test
    fun `로그아웃과 같은 초에 발급된 refresh는 살려둔다 - 직후 재로그인이 스스로를 거절하지 않게`() {
        // JWT iat는 초 단위다. 로그아웃이 10:00:00.400에 찍혔고 재로그인 토큰의 iat가 10:00:00이면
        // 초 아래를 버리고 비교해야 새 토큰이 통과한다
        userPort.put(account(7L, tokensInvalidBefore = Instant.parse("2026-09-08T10:00:00.400Z")))

        assertTrue(service.isRefreshAllowed(7L, Instant.parse("2026-09-08T10:00:00Z")))
    }

    @Test
    fun `사용자가 없으면 거절한다 - 발급해 줄 대상이 없다`() {
        assertFalse(service.isRefreshAllowed(999L, Instant.parse("2026-09-08T10:00:00Z")))
    }

    @Test
    fun `로그아웃은 지금 시각을 무효 기준으로 찍는다`() {
        userPort.put(account(7L, tokensInvalidBefore = null))
        val before = Instant.now()

        service.logout(7L)

        val cutoff = userPort.invalidations.single().second
        assertEquals(7L, userPort.invalidations.single().first)
        assertFalse(cutoff.isBefore(before), "무효 기준은 호출 시각 이후여야 한다")
    }

    @Test
    fun `로그아웃을 두 번 해도 실패가 아니다 - 기준만 앞으로 간다`() {
        userPort.put(account(7L, tokensInvalidBefore = null))

        service.logout(7L)
        service.logout(7L)

        val (first, second) = userPort.invalidations.map { it.second }
        assertFalse(second.isBefore(first))
    }

    @Test
    fun `없는 사용자의 로그아웃도 예외가 아니다 - 폐기할 것이 없을 뿐이다`() {
        service.logout(999L)

        assertEquals(1, userPort.invalidations.size)
    }

    private fun account(
        id: Long,
        tokensInvalidBefore: Instant?,
    ) = UserAccount(
        id = id,
        kakaoId = id * 100,
        nickname = "사용자$id",
        profileImageUrl = null,
        profileImageAssetId = null,
        profileCompletedAt = Instant.parse("2026-09-01T00:00:00Z"),
        tokensInvalidBefore = tokensInvalidBefore,
    )

    private class FakeUserAccountPort : UserAccountPort {
        private val accounts = mutableMapOf<Long, UserAccount>()
        val invalidations = mutableListOf<Pair<Long, Instant>>()

        fun put(account: UserAccount) {
            accounts[account.id] = account
        }

        override fun findByKakaoId(kakaoId: Long): UserAccount? = accounts.values.firstOrNull { it.kakaoId == kakaoId }

        override fun findById(userId: Long): UserAccount? = accounts[userId]

        override fun create(
            kakaoId: Long,
            nickname: String,
        ): UserAccount? = error("이 테스트에서는 쓰지 않는다")

        override fun updateProfile(
            userId: Long,
            nickname: String,
            profileImageAssetId: Long?,
            completedAt: Instant,
        ): UserAccount? = error("이 테스트에서는 쓰지 않는다")

        override fun invalidateTokensIssuedBefore(
            userId: Long,
            at: Instant,
        ): Boolean {
            invalidations += userId to at
            val current = accounts[userId] ?: return false
            accounts[userId] = current.copy(tokensInvalidBefore = at)
            return true
        }
    }
}
