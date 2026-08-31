package com.tmt.application.domain.auth

import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.output.auth.KakaoAuthPort
import com.tmt.application.port.output.auth.KakaoProfile
import com.tmt.application.port.output.persistence.GroupJoinTicketPort
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KakaoLoginServiceTest {
    private val authPort = FakeKakaoAuthPort()
    private val userPort = FakeUserAccountPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val service = KakaoLoginService(authPort, userPort, UserRegistrationService(userPort, ticketPort))

    @Test
    fun `처음 온 카카오 계정이면 사용자를 만들고 isNewUser=true다`() {
        authPort.profile = KakaoProfile(kakaoId = 12345L, nickname = "준형이", profileImageUrl = "https://img")

        val result = service.login(command())

        assertTrue(result.isNewUser)
        assertEquals("준형이", result.nickname)
        assertEquals("https://img", result.profileImageUrl)
        assertEquals(12345L, userPort.accounts.single().kakaoId)
        // 가입 보상 티켓 1장 (T2)
        assertEquals(listOf(userPort.accounts.single().id), ticketPort.signupGrants)
    }

    @Test
    fun `이미 있는 카카오 계정이면 만들지 않고 isNewUser=false다`() {
        authPort.profile = KakaoProfile(kakaoId = 12345L, nickname = "준형이", profileImageUrl = null)
        service.login(command())

        val result = service.login(command())

        assertFalse(result.isNewUser)
        assertEquals(1, userPort.accounts.size)
    }

    @Test
    fun `인가 코드와 리다이렉트 URI를 그대로 카카오 포트에 넘긴다`() {
        service.login(KakaoLoginCommand(code = "auth-code", redirectUri = "http://localhost:3000/cb"))

        assertEquals("auth-code" to "http://localhost:3000/cb", authPort.calls.single())
    }

    @Test
    fun `카카오 닉네임이 없으면 기본 닉네임으로 만든다`() {
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = null, profileImageUrl = null)

        val result = service.login(command())

        assertEquals("또맛또 미식가", result.nickname)
    }

    @Test
    fun `카카오 닉네임이 2자 미만이면 기본 닉네임으로 만든다`() {
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = "김", profileImageUrl = null)

        val result = service.login(command())

        assertEquals("또맛또 미식가", result.nickname)
    }

    @Test
    fun `카카오 닉네임이 10자를 넘으면 10자로 자른다`() {
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = "열자를넘는아주긴닉네임", profileImageUrl = null)

        val result = service.login(command())

        assertEquals("열자를넘는아주긴닉네", result.nickname)
    }

    @Test
    fun `동시 로그인 경쟁에서 지면 먼저 들어간 사용자로 isNewUser=false다`() {
        authPort.profile = KakaoProfile(kakaoId = 777L, nickname = "준형이", profileImageUrl = null)
        userPort.rejectCreate = true

        val result = service.login(command())

        assertFalse(result.isNewUser)
        assertEquals(777L, userPort.accounts.single().kakaoId)
    }

    private fun command() = KakaoLoginCommand(code = "auth-code", redirectUri = "http://localhost:3000/cb")

    private class FakeGroupJoinTicketPort : GroupJoinTicketPort {
        val signupGrants = mutableListOf<Long>()

        override fun countAvailable(userId: Long): Int = signupGrants.count { it == userId }

        override fun grantForReview(
            userId: Long,
            reviewId: Long,
        ) = error("이 테스트에서 쓰지 않는다")

        override fun grantForSignup(userId: Long) {
            signupGrants += userId
        }
    }

    private class FakeKakaoAuthPort : KakaoAuthPort {
        var profile = KakaoProfile(kakaoId = 1L, nickname = null, profileImageUrl = null)
        val calls = mutableListOf<Pair<String, String>>()

        override fun fetchProfile(
            code: String,
            redirectUri: String,
        ): KakaoProfile {
            calls += code to redirectUri
            return profile
        }
    }

    private class FakeUserAccountPort : UserAccountPort {
        val accounts = mutableListOf<UserAccount>()

        /** 동시 로그인 경쟁에서 진 상황 재현 — 다른 요청이 먼저 만든 행이 이미 있다 */
        var rejectCreate = false
        private var nextId = 1L

        override fun findByKakaoId(kakaoId: Long): UserAccount? = accounts.firstOrNull { it.kakaoId == kakaoId }

        override fun create(
            kakaoId: Long,
            nickname: String,
            profileImageUrl: String?,
        ): UserAccount? {
            if (rejectCreate) {
                accounts += UserAccount(id = nextId++, kakaoId = kakaoId, nickname = "먼저온사람", profileImageUrl = null)
                return null
            }
            val account =
                UserAccount(id = nextId++, kakaoId = kakaoId, nickname = nickname, profileImageUrl = profileImageUrl)
            accounts += account
            return account
        }
    }
}
