package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/** 발급 결과. 만료는 시각이 아니라 남은 초로 준다 — 클라이언트 시계가 서버와 다를 수 있다 */
data class IssuedTokens(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val refreshToken: String,
)

/** access를 refresh 자리에(또는 반대로) 꽂는 것을 막는 용도 구분 클레임 */
enum class TokenUse { ACCESS, REFRESH }

/** 검증을 통과한 토큰의 내용. [issuedAt]은 로그아웃 이전 발급분을 거절하는 데 쓴다 (TMT-353) */
data class TokenClaims(
    val userId: Long,
    val issuedAt: Instant,
)

/**
 * 세션·토큰 (TMT-272) — HS256 JWT, 저장소 없이 서명 검증만으로 동작한다(stateless).
 * 무효화도 토큰을 저장해서가 아니라 **발급 시각 컷오프**로 한다 — 로그아웃이 사용자 행에 시각을 찍고,
 * 재발급이 [TokenClaims.issuedAt]을 그 값과 비교한다 (TMT-353). access는 짧은 만료로 흘려보낸다.
 */
@Component
class JwtTokenCodec(
    @param:Value("\${tmt.auth.token.secret}") secret: String,
    @param:Value("\${tmt.auth.token.access-ttl:PT1H}") private val accessTtl: Duration,
    @param:Value("\${tmt.auth.token.refresh-ttl:P7D}") private val refreshTtl: Duration,
) {
    // 키가 비면 기동을 막는다 — 코드에 박힌 기본값으로 서명하면 위조 방지가 무의미해진다 (TMT-191과 같은 이유)
    private val key: SecretKey =
        Keys.hmacShaKeyFor(
            secret.ifBlank { throw IllegalStateException("tmt.auth.token.secret이 비어 있다") }.toByteArray(),
        )

    fun issue(userId: Long): IssuedTokens =
        IssuedTokens(
            accessToken = encode(userId, TokenUse.ACCESS, accessTtl),
            accessTokenExpiresIn = accessTtl.seconds,
            refreshToken = encode(userId, TokenUse.REFRESH, refreshTtl),
        )

    /** 실패는 전부 401 — 만료([ErrorCode.AUTH_TOKEN_EXPIRED])만 구분해 FE가 재발급으로 분기한다 */
    fun parseUserId(
        token: String,
        expectedUse: TokenUse,
    ): Long = parse(token, expectedUse).userId

    /** [parseUserId]와 같은 검증에 발급 시각을 더한다 — 재발급이 로그아웃 이전 발급분을 걸러내는 데 쓴다 */
    fun parse(
        token: String,
        expectedUse: TokenUse,
    ): TokenClaims {
        val claims =
            try {
                Jwts
                    .parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (e: ExpiredJwtException) {
                throw TmtException(ErrorCode.AUTH_TOKEN_EXPIRED)
            } catch (e: JwtException) {
                throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
            } catch (e: IllegalArgumentException) {
                throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
            }
        if (claims[USE_CLAIM] != expectedUse.name) throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
        val userId = claims.subject?.toLongOrNull() ?: throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
        // 우리가 발급한 토큰에는 항상 있다 — 없으면 우리 것이 아니다
        val issuedAt = claims.issuedAt?.toInstant() ?: throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
        return TokenClaims(userId, issuedAt)
    }

    private fun encode(
        userId: Long,
        use: TokenUse,
        ttl: Duration,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim(USE_CLAIM, use.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact()
    }

    companion object {
        private const val USE_CLAIM = "use"
    }
}
