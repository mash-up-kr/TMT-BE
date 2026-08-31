package com.tmt.application.domain.auth

import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRegistrationServiceTest {
    private val userPort = FakeUserAccountPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val service = UserRegistrationService(userPort, ticketPort)

    @Test
    fun `가입하면 보상 티켓 1장을 발급한다 - T2`() {
        val created = service.register(kakaoId = 12345L, nickname = "준형이", profileImageUrl = null)

        assertNotNull(created)
        assertEquals(listOf(created.id), ticketPort.signupGrants)
    }

    @Test
    fun `동시 로그인 경쟁에서 지면 null이고 티켓도 발급하지 않는다`() {
        userPort.rejectCreate = true

        val created = service.register(kakaoId = 12345L, nickname = "준형이", profileImageUrl = null)

        assertNull(created)
        assertEquals(emptyList(), ticketPort.signupGrants)
    }

    @Test
    fun `보상 발급이 실패해도 가입은 성공한다 - 로그인까지 막지 않는다`() {
        ticketPort.failGrant = true

        val created = service.register(kakaoId = 12345L, nickname = "준형이", profileImageUrl = null)

        assertNotNull(created)
    }

    private class FakeUserAccountPort : UserAccountPort {
        var rejectCreate = false
        private var nextId = 1L

        override fun findByKakaoId(kakaoId: Long): UserAccount? = null

        override fun create(
            kakaoId: Long,
            nickname: String,
            profileImageUrl: String?,
        ): UserAccount? {
            if (rejectCreate) return null
            return UserAccount(id = nextId++, kakaoId = kakaoId, nickname = nickname, profileImageUrl = profileImageUrl)
        }
    }

    private class FakeGroupJoinTicketPort : GroupJoinTicketPort {
        val signupGrants = mutableListOf<Long>()
        var failGrant = false

        override fun countAvailable(userId: Long): Int = signupGrants.count { it == userId }

        override fun grantForReview(
            userId: Long,
            reviewId: Long,
        ) = error("이 테스트에서 쓰지 않는다")

        override fun grantForSignup(userId: Long) {
            if (failGrant) error("DB 순단")
            signupGrants += userId
        }
    }
}
