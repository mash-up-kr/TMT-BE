package com.tmt.input.http.controller.paging

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CursorCodecTest {
    private val condition = CursorCondition.of("UPDATED_AT_DESC", "user_1")

    @Test
    fun `받은 커서를 그대로 돌려주면 같은 정렬 키가 나온다`() {
        val key = SaveKey(Instant.parse("2026-08-20T10:00:00Z"), 37)

        val cursor = CursorCodec.encode(SaveCursorSpec, key, condition)

        assertEquals(key, CursorCodec.decode(SaveCursorSpec, cursor, condition))
    }

    @Test
    fun `커서는 URL에 그대로 실을 수 있다`() {
        val cursor = CursorCodec.encode(SaveCursorSpec, SaveKey(Instant.EPOCH, 1), condition)

        assertTrue(cursor.matches(Regex("[A-Za-z0-9_-]+")), cursor)
    }

    @Test
    fun `커서가 없으면 첫 페이지다`() {
        assertNull(CursorCodec.decode(SaveCursorSpec, null, condition))
    }

    @Test
    fun `조건이 바뀌면 이전 커서는 무효다`() {
        val cursor = CursorCodec.encode(SaveCursorSpec, SaveKey(Instant.EPOCH, 1), condition)
        val changed = CursorCondition.of("UPDATED_AT_DESC", "user_2")

        val error = assertFailsWith<TmtException> { CursorCodec.decode(SaveCursorSpec, cursor, changed) }

        assertEquals(ErrorCode.INVALID_CURSOR, error.errorCode)
    }

    @Test
    fun `조건 값의 경계가 달라도 다른 커서다`() {
        val ab = CursorCondition.of("ab", "c")
        val a = CursorCondition.of("a", "bc")
        val cursor = CursorCodec.encode(SaveCursorSpec, SaveKey(Instant.EPOCH, 1), ab)

        assertFailsWith<TmtException> { CursorCodec.decode(SaveCursorSpec, cursor, a) }
    }

    @Test
    fun `해석할 수 없는 커서는 거절한다`() {
        listOf("not-base64!!", "", "eyJrIjpbXX0", base64Url("{\"k\":[\"only-one\"],\"h\":\"x\"}")).forEach { cursor ->
            val error = assertFailsWith<TmtException>(cursor) { CursorCodec.decode(SaveCursorSpec, cursor, condition) }
            assertEquals(ErrorCode.INVALID_CURSOR, error.errorCode, cursor)
        }
    }

    @Test
    fun `정렬 키 형식이 어긋나면 거절한다`() {
        val cursor = CursorCodec.encode(WrongTypeSpec, "이건 시각이 아니다", condition)

        assertFailsWith<TmtException> { CursorCodec.decode(SaveCursorSpec, cursor, condition) }
    }

    private fun base64Url(json: String): String =
        java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray())

    /** 키 1개짜리 스펙 — 같은 커서를 2개짜리 스펙으로 읽으면 형식이 어긋난다. */
    private object WrongTypeSpec : CursorSpec<String> {
        override fun toKeys(key: String) = listOf(key, "1")

        override fun fromKeys(keys: List<String>) = keys.first()
    }
}

internal data class SaveKey(
    val updatedAt: Instant,
    val saveId: Long,
)

internal object SaveCursorSpec : CursorSpec<SaveKey> {
    override fun toKeys(key: SaveKey) = listOf(key.updatedAt.toString(), key.saveId.toString())

    override fun fromKeys(keys: List<String>): SaveKey {
        require(keys.size == 2) { "정렬 키 2개가 필요하다" }
        return SaveKey(Instant.parse(keys[0]), keys[1].toLong())
    }
}
