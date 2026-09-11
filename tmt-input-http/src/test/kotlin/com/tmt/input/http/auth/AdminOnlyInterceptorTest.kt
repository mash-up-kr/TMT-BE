package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 어드민 가드 (TMT-421). 판정 자체는 설정 허용 목록이고, **설정 누락이 전원 허용으로
 * 기울지 않는지**가 이 테스트의 핵심이다.
 */
class AdminOnlyInterceptorTest {
    private fun interceptor(rawUserIds: String) = AdminOnlyInterceptor(AdminAllowlist(rawUserIds))

    private fun request(userId: Long?) =
        MockHttpServletRequest().apply {
            userId?.let { setAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, it) }
        }

    private fun preHandle(
        rawUserIds: String,
        userId: Long?,
    ) = interceptor(rawUserIds).preHandle(request(userId), MockHttpServletResponse(), Any())

    @Test
    fun `허용 목록에 있으면 통과한다`() {
        assertTrue(preHandle("1,2,3", userId = 2))
    }

    @Test
    fun `허용 목록에 없으면 ADMIN_REQUIRED다`() {
        val e = assertThrows { preHandle("1,2,3", userId = 4) }
        assertEquals(ErrorCode.ADMIN_REQUIRED, e.errorCode)
    }

    @Test
    fun `설정이 비어 있으면 아무도 어드민이 아니다`() {
        // 기본값이 열려 있는 쪽이 사고다 — 닫혀 있으면 403을 보고 설정을 채우면 된다
        listOf("", "   ", ",,").forEach { raw ->
            val e = assertThrows { preHandle(raw, userId = 1) }
            assertEquals(ErrorCode.ADMIN_REQUIRED, e.errorCode, "raw=[$raw]에서 통과했다")
        }
        assertFalse(AdminAllowlist("").isAdmin(1))
    }

    @Test
    fun `토큰이 없으면 401이다 — 인자 해석보다 먼저 끊는다`() {
        val e = assertThrows { preHandle("1", userId = null) }
        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode)
    }

    @Test
    fun `공백과 잘못된 항목은 무시하고 숫자만 읽는다`() {
        val allowlist = AdminAllowlist(" 1 , abc, ,2,")

        assertTrue(allowlist.isAdmin(1))
        assertTrue(allowlist.isAdmin(2))
        // 파싱 실패 항목이 조용히 누구든 허용하는 값으로 바뀌지 않는다
        assertFalse(allowlist.isAdmin(0))
        assertFalse(allowlist.isAdmin(3))
    }

    private fun assertThrows(block: () -> Unit): TmtException =
        runCatching(block).exceptionOrNull() as? TmtException
            ?: error("TmtException이 나오지 않았다")
}
