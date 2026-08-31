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

/**
 * 세션·토큰 (TMT-272) — HS256 JWT, 저장소 없이 서명 검증만으로 동작한다(stateless).
 * refresh는 만료 전까지 서버가 무효화할 수 없다 — 로그아웃은 클라이언트 삭제로 처리하고,
 * 강제 무효화가 필요해지면 그때 저장소를 붙인다.
 */
@Component
class JwtTokenCodec(
    @param:Value("\${tmt.auth.token.secret}") secret: String,
    @param:Value("\${tmt.auth.token.access-ttl:PT1H}") private val accessTtl: Duration,
    @param:Value("\${tmt.auth.token.refresh-ttl:P30D}") private val refreshTtl: Duration,
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
    ): Long {
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
        return claims.subject?.toLongOrNull() ?: throw TmtException(ErrorCode.AUTH_TOKEN_INVALID)
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
