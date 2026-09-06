package com.tmt.output.persistence.postgres.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LikePatternsTest {
    @Test
    fun `메타문자 세 종을 백슬래시로 이스케이프한다`() {
        assertEquals("""100\%""", LikePatterns.escape("100%"))
        assertEquals("""a\_b""", LikePatterns.escape("a_b"))
        assertEquals("""c\\d""", LikePatterns.escape("""c\d"""))
    }

    @Test
    fun `부분 일치 패턴은 앞뒤 %를 붙이고 안쪽만 이스케이프한다`() {
        assertEquals("""%\%%""", LikePatterns.contains("%"))
        assertEquals("%사사노하%", LikePatterns.contains("사사노하"))
    }

    @Test
    fun `null과 빈 문자열은 검색 없음이라 null이다`() {
        assertNull(LikePatterns.contains(null))
        assertNull(LikePatterns.contains(""))
    }
}
