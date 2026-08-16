package com.tmt.input.http.mock

import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MockCursorTest {
    private val source = (1..120).map { "item_$it" }

    @Test
    fun `limit이 없으면 기본 20건이다 (규약 §5-2)`() {
        val page = MockCursor.paginate(source, cursor = null, limit = null) { it }

        assertEquals(MockCursor.DEFAULT_LIMIT, page.items.size)
    }

    @Test
    fun `상한 50을 넘겨 요청하면 상한으로 절삭한다`() {
        val page = MockCursor.paginate(source, cursor = null, limit = 100) { it }

        assertEquals(MockCursor.MAX_LIMIT, page.items.size)
    }

    @Test
    fun `limit이 0이면 같은 커서가 반복되지 않게 최소 1건을 내린다`() {
        val page = MockCursor.paginate(source, cursor = null, limit = 0) { it }

        assertEquals(1, page.items.size)
        assertEquals("item_1", page.items.first())
    }

    @Test
    fun `limit이 음수여도 예외 없이 최소 1건을 내린다`() {
        val page = MockCursor.paginate(source, cursor = null, limit = -1) { it }

        assertEquals(1, page.items.size)
    }

    @Test
    fun `커서를 따라가면 끝까지 순회하고 마지막 페이지는 nextCursor가 null이다`() {
        var cursor: String? = null
        val collected = mutableListOf<String>()
        repeat(3) {
            val page = MockCursor.paginate(source, cursor, limit = 50) { it }
            collected += page.items
            cursor = page.nextCursor
        }

        assertEquals(source, collected)
        assertNull(cursor)
    }

    @Test
    fun `마지막 페이지는 hasNext가 false다`() {
        val page = MockCursor.paginate(source.take(10), cursor = null, limit = 50) { it }

        assertFalse(page.hasNext)
        assertNull(page.nextCursor)
    }

    @Test
    fun `해석할 수 없는 커서는 INVALID_CURSOR다`() {
        val e = assertThrows<TmtException> { MockCursor.paginate(source, "not-a-cursor", null) { it } }

        assertEquals("INVALID_CURSOR", e.errorCode.name)
    }
}
