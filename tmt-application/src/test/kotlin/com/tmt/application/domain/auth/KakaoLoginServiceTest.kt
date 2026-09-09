package com.tmt.application.domain.auth

import com.tmt.application.domain.save.FakeGroupJoinTicketPort
import com.tmt.application.port.input.GetMediaUrlsUseCase
import com.tmt.application.port.input.KakaoLoginCommand
import com.tmt.application.port.output.auth.KakaoAuthPort
import com.tmt.application.port.output.auth.KakaoProfile
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoLoginServiceTest {
    private val authPort = FakeKakaoAuthPort()
    private val userPort = FakeUserAccountPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val mediaUrls = FakeGetMediaUrlsUseCase()
    private val service = KakaoLoginService(authPort, userPort, ticketPort, mediaUrls)

    @Test
    fun `처음 온 카카오 계정이면 사용자를 만들고 isNewUser=true다`() {
        authPort.profile = KakaoProfile(kakaoId = 12345L, nickname = "준형이", profileImageUrl = "https://img")

        val result = service.login(command())

        assertTrue(result.isNewUser)
        assertEquals("준형이", result.nickname)
        // 카카오 프로필 사진은 저장하지 않는다 — 가입 완결에서 사용자가 고른 사진만 프로필이 된다 (TMT-370)
        assertNull(result.profileImageUrl)
        assertFalse(result.profileCompleted)
        assertEquals(12345L, userPort.accounts.single().kakaoId)
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
    fun `카카오 닉네임이 20자를 넘으면 20자로 자른다`() {
        // 상한은 V6에서 10자 → 20자로 늘었다 (U3 확정, TMT-350)
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = "가나다라마바사아자차카타파하거너더러머버서어저처커", profileImageUrl = null)

        val result = service.login(command())

        assertEquals("가나다라마바사아자차카타파하거너더러머버", result.nickname)
        assertEquals(20, result.nickname.length)
    }

    @Test
    fun `20자 이하 닉네임은 그대로 쓴다`() {
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = "열자를넘는아주긴닉네임", profileImageUrl = null)

        val result = service.login(command())

        assertEquals("열자를넘는아주긴닉네임", result.nickname)
    }

    @Test
    fun `이모지 닉네임을 잘라도 서로게이트가 깨지지 않는다`() {
        // take()는 UTF-16 코드 유닛이라 21번째 유닛에서 자르면 이모지 반쪽이 남는다.
        // CHECK(users_nickname_len)는 char_length라 코드포인트로 세므로 절단도 같아야 한다
        authPort.profile = KakaoProfile(kakaoId = 1L, nickname = "\uD83C\uDF55".repeat(25), profileImageUrl = null)

        val result = service.login(command())

        assertEquals(20, result.nickname.codePointCount(0, result.nickname.length))
        assertEquals("\uD83C\uDF55".repeat(20), result.nickname)
    }

    @Test
    fun `처음 온 계정에는 가입 보상 티켓 1장이 발급된다`() {
        authPort.profile = KakaoProfile(kakaoId = 12345L, nickname = "준형이", profileImageUrl = null)

        val result = service.login(command())

        // T2 — 가입 선물 1장. 마이페이지의 availableTicketCount가 0이 아닌 근거다
        assertEquals(listOf(result.userId), ticketPort.signupGrants)
        assertEquals(1, ticketPort.countAvailable(result.userId))
    }

    @Test
    fun `이미 있는 계정으로 다시 로그인해도 티켓을 또 주지 않는다`() {
        userPort.accounts += account(id = 7L, kakaoId = 999L, nickname = "준형이")
        authPort.profile = KakaoProfile(kakaoId = 999L, nickname = "준형이", profileImageUrl = null)

        service.login(command())

        assertEquals(emptyList(), ticketPort.signupGrants)
    }

    @Test
    fun `경쟁에서 진 쪽은 티켓을 발급하지 않는다`() {
        // 이긴 쪽이 이미 발급했다 — 여기서 또 주면 한 계정에 두 장이 된다
        authPort.profile = KakaoProfile(kakaoId = 777L, nickname = "준형이", profileImageUrl = null)
        userPort.rejectCreate = true

        service.login(command())

        assertEquals(emptyList(), ticketPort.signupGrants)
    }

    @Test
    fun `동시 로그인 경쟁에서 지면 먼저 들어간 사용자로 isNewUser=false다`() {
        authPort.profile = KakaoProfile(kakaoId = 777L, nickname = "준형이", profileImageUrl = null)
        userPort.rejectCreate = true

        val result = service.login(command())

        assertFalse(result.isNewUser)
        assertEquals(777L, userPort.accounts.single().kakaoId)
    }

    private fun account(
        id: Long,
        kakaoId: Long,
        nickname: String,
        profileCompletedAt: Instant? = null,
    ) = UserAccount(
        id = id,
        kakaoId = kakaoId,
        nickname = nickname,
        profileImageUrl = null,
        profileImageAssetId = null,
        profileCompletedAt = profileCompletedAt,
        tokensInvalidBefore = null,
    )

    private fun command() = KakaoLoginCommand(code = "auth-code", redirectUri = "http://localhost:3000/cb")

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

        override fun findById(userId: Long): UserAccount? = accounts.firstOrNull { it.id == userId }

        override fun create(
            kakaoId: Long,
            nickname: String,
        ): UserAccount? {
            if (rejectCreate) {
                accounts += blank(nextId++, kakaoId, "먼저온사람")
                return null
            }
            val account = blank(nextId++, kakaoId, nickname)
            accounts += account
            return account
        }

        override fun invalidateTokensIssuedBefore(
            userId: Long,
            at: Instant,
        ): Boolean = accounts.any { it.id == userId }

        override fun updateProfile(
            userId: Long,
            nickname: String,
            profileImageAssetId: Long?,
            completedAt: Instant,
        ): UserAccount? = accounts.firstOrNull { it.id == userId }

        private fun blank(
            id: Long,
            kakaoId: Long,
            nickname: String,
        ) = UserAccount(
            id = id,
            kakaoId = kakaoId,
            nickname = nickname,
            profileImageUrl = null,
            profileImageAssetId = null,
            profileCompletedAt = null,
            tokensInvalidBefore = null,
        )
    }

    private class FakeGetMediaUrlsUseCase : GetMediaUrlsUseCase {
        override fun urlsOf(assetIds: List<Long>): Map<Long, String> =
            assetIds.associateWith { "https://media.example.com/photo/$it" }
    }
}
