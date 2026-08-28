package com.tmt.application.domain.address

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AddressQuerySanitizerTest {
    @Test
    fun `특수문자는 제거된다`() {
        val sanitized = AddressQuerySanitizer.sanitize("오목로32길' OR 1=1; --")
        assertFalse(sanitized.any { it in "'=;<>%\"()" }, "특수문자가 남았다: $sanitized")
    }

    @Test
    fun `SQL 예약어 토큰은 제거된다`() {
        assertEquals("오목로32길 1 1", AddressQuerySanitizer.sanitize("오목로32길' OR 1=1"))
        assertEquals("가게", AddressQuerySanitizer.sanitize("union select 가게 from where"))
    }

    @Test
    fun `예약어를 부분 문자열로 포함한 검색어는 통과한다`() {
        assertEquals("ORIGIN", AddressQuerySanitizer.sanitize("ORIGIN"))
        assertEquals("UNIONMALL", AddressQuerySanitizer.sanitize("UNIONMALL"))
        assertEquals("ORIGIN 카페", AddressQuerySanitizer.sanitize("ORIGIN 카페"))
    }

    @Test
    fun `지번의 하이픈은 남는다`() {
        assertEquals("신정동 948-1", AddressQuerySanitizer.sanitize("신정동 948-1"))
    }

    @Test
    fun `정제 후 2자 미만이면 VALIDATION_FAILED다`() {
        // 전부 예약어·특수문자라 정제 후 아무것도 남지 않는다
        val e = assertFailsWith<TmtException> { AddressQuerySanitizer.sanitizeOrThrow("OR ';'") }
        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)

        assertFailsWith<TmtException> { AddressQuerySanitizer.sanitizeOrThrow("가") }
        assertFailsWith<TmtException> { AddressQuerySanitizer.sanitizeOrThrow(null) }
    }

    @Test
    fun `정제는 멱등이다 - 어댑터에서 다시 적용해도 결과가 같다`() {
        val once = AddressQuerySanitizer.sanitize("양천구 OR 오목로32길'")
        assertEquals(once, AddressQuerySanitizer.sanitize(once))
    }
}
