package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그아웃 기준 시각의 저장·재조회 (TMT-353). 어댑터가 자기 트랜잭션에서 엔티티를 바꾸고 더티 체킹으로
 * 흘려보내는 경로라, 실제 DB에 태워야 커밋 뒤에도 값이 남는지가 확인된다.
 */
@Import(UserAccountAdapter::class)
class UserAccountAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: UserAccountAdapter

    @Test
    fun `로그아웃 기준 시각이 저장되고 다시 읽힌다`() {
        val userId = fixtures.newUser("로그아웃")
        // timestamptz는 마이크로초까지 보존한다 — 초 아래가 살아서 돌아와야 폐기 판정의 절삭이 의미가 있다
        val at = Instant.parse("2026-09-08T10:00:00.400Z")
        assertNull(adapter.findById(userId)?.tokensInvalidBefore, "로그아웃한 적 없는 사용자는 null이다")

        assertTrue(adapter.invalidateTokensIssuedBefore(userId, at))

        assertEquals(at, adapter.findById(userId)?.tokensInvalidBefore)
    }

    @Test
    fun `두 번째 로그아웃은 기준 시각을 덮어쓴다`() {
        val userId = fixtures.newUser("두번로그아웃")
        adapter.invalidateTokensIssuedBefore(userId, Instant.parse("2026-09-08T10:00:00Z"))

        adapter.invalidateTokensIssuedBefore(userId, Instant.parse("2026-09-08T11:00:00Z"))

        assertEquals(Instant.parse("2026-09-08T11:00:00Z"), adapter.findById(userId)?.tokensInvalidBefore)
    }

    @Test
    fun `없는 사용자는 false다 - 폐기할 것이 없다`() {
        assertFalse(adapter.invalidateTokensIssuedBefore(-1L, Instant.now()))
    }
}
