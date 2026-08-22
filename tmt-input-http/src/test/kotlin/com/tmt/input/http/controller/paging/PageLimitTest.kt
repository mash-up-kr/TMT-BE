package com.tmt.input.http.controller.paging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PageLimitTest {
    @Test
    fun `미지정이면 20이다`() {
        assertEquals(20, PageLimit.of(null))
    }

    @Test
    fun `상한을 넘으면 50으로 자른다`() {
        assertEquals(50, PageLimit.of(51))
        assertEquals(50, PageLimit.of(1_000))
    }

    @Test
    fun `0과 음수는 1로 올린다`() {
        assertEquals(1, PageLimit.of(0))
        assertEquals(1, PageLimit.of(-5))
    }

    @Test
    fun `범위 안의 값은 그대로 쓴다`() {
        assertEquals(30, PageLimit.of(30))
    }
}
