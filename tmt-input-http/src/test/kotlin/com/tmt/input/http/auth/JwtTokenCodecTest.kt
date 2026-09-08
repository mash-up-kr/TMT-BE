package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtTokenCodecTest {
    private val codec = codec()

    @Test
    fun `발급한 access 토큰에서 사용자 ID를 읽는다`() {
        val tokens = codec.issue(42L)

        assertEquals(42L, codec.parseUserId(tokens.accessToken, TokenUse.ACCESS))
        assertEquals(Duration.ofHours(1).seconds, tokens.accessTokenExpiresIn)
    }

    @Test
    fun `발급한 refresh 토큰에서 사용자 ID를 읽는다`() {
        val tokens = codec.issue(42L)

        assertEquals(42L, codec.parseUserId(tokens.refreshToken, TokenUse.REFRESH))
    }

    @Test
    fun `parse는 사용자 ID와 발급 시각을 함께 준다 - 발급 시각은 초 단위다 (TMT-353)`() {
        val before = Instant.now()
        val tokens = codec.issue(42L)

        val claims = codec.parse(tokens.refreshToken, TokenUse.REFRESH)

        assertEquals(42L, claims.userId)
        // JWT iat는 초 단위(NumericDate)라 나노초가 잘린다 — 폐기 판정이 이 정밀도를 전제한다
        assertEquals(0, claims.issuedAt.nano)
        assertTrue(!claims.issuedAt.isBefore(before.minusSeconds(1)) && !claims.issuedAt.isAfter(Instant.now()))
    }

    @Test
    fun `access 토큰을 refresh 자리에 꽂으면 AUTH_TOKEN_INVALID다`() {
        val tokens = codec.issue(42L)

        val e = assertFailsWith<TmtException> { codec.parseUserId(tokens.accessToken, TokenUse.REFRESH) }

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, e.errorCode)
    }

    @Test
    fun `만료된 토큰은 AUTH_TOKEN_EXPIRED다`() {
        val expired = codec(accessTtl = Duration.ofSeconds(-10))

        val e =
            assertFailsWith<TmtException> {
                codec.parseUserId(expired.issue(42L).accessToken, TokenUse.ACCESS)
            }

        assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, e.errorCode)
    }

    @Test
    fun `토큰이 아닌 문자열은 AUTH_TOKEN_INVALID다`() {
        val e = assertFailsWith<TmtException> { codec.parseUserId("not-a-token", TokenUse.ACCESS) }

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, e.errorCode)
    }

    @Test
    fun `다른 키로 서명한 토큰은 AUTH_TOKEN_INVALID다`() {
        val other = codec(secret = "another-secret-that-is-32-bytes-long!")

        val e =
            assertFailsWith<TmtException> {
                codec.parseUserId(other.issue(42L).accessToken, TokenUse.ACCESS)
            }

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, e.errorCode)
    }

    @Test
    fun `서명 키가 비어 있으면 기동을 막는다`() {
        val e = assertFailsWith<IllegalStateException> { codec(secret = "") }

        assertTrue(e.message!!.contains("tmt.auth.token.secret"))
    }

    private fun codec(
        secret: String = "test-jwt-secret-that-is-32-bytes-long",
        accessTtl: Duration = Duration.ofHours(1),
        refreshTtl: Duration = Duration.ofDays(30),
    ) = JwtTokenCodec(secret, accessTtl, refreshTtl)
}
