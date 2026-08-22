package com.tmt.input.http.controller.paging

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 커서가 실제 페이징에서 무엇을 보장해야 하는지를 고정한다.
 * 정렬은 이어쓰기 목록과 같은 `updatedAt DESC, saveId DESC`다 (규약 §5-3).
 */
class KeysetSliceTest {
    private val condition = CursorCondition.of("UPDATED_AT_DESC", "user_1")

    @Test
    fun `같은 정렬 키가 경계에 걸려도 중복도 누락도 없다`() {
        // updatedAt이 같은 3건이 2개씩 끊는 페이지 경계에 걸린다
        val rows =
            listOf(
                row(3, "2026-08-20T12:00:00Z"),
                row(2, "2026-08-20T10:00:00Z"),
                row(1, "2026-08-20T10:00:00Z"),
                row(4, "2026-08-20T10:00:00Z"),
                row(5, "2026-08-19T09:00:00Z"),
            )

        val read = readAll(rows, limit = 2)

        assertEquals(listOf(3L, 4L, 2L, 1L, 5L), read)
    }

    @Test
    fun `페이지 사이에 행이 들어와도 이미 본 행을 다시 주지 않는다`() {
        val rows = (1L..5L).map { row(it, "2026-08-2${it}T10:00:00Z") }.toMutableList()

        val first = read(rows, cursor = null, limit = 2)
        // 1페이지를 본 뒤 가장 최신 행이 추가된다 — offset이었다면 전체가 밀려 2페이지가 1페이지를 반복한다
        rows += row(6, "2026-08-26T10:00:00Z")
        val rest = readAll(rows, limit = 2, cursor = first.nextCursor)

        assertEquals(listOf(5L, 4L), first.ids)
        assertEquals(listOf(3L, 2L, 1L), rest)
    }

    @Test
    fun `페이지 사이에 행이 지워져도 남은 행을 건너뛰지 않는다`() {
        val rows = (1L..5L).map { row(it, "2026-08-2${it}T10:00:00Z") }.toMutableList()

        val first = read(rows, cursor = null, limit = 2)
        rows.removeIf { it.saveId == 2L }
        val rest = readAll(rows, limit = 2, cursor = first.nextCursor)

        assertEquals(listOf(5L, 4L), first.ids)
        assertEquals(listOf(3L, 1L), rest)
    }

    /** 커서를 따라 끝까지 읽는다. */
    private fun readAll(
        rows: List<Row>,
        limit: Int,
        cursor: String? = null,
    ): List<Long> {
        val ids = mutableListOf<Long>()
        var next = cursor
        do {
            val page = read(rows, next, limit)
            ids += page.ids
            next = page.nextCursor
        } while (next != null)
        return ids
    }

    /** 키셋 조회 한 페이지 — 실구현의 `WHERE (updated_at, save_id) < (?, ?)`에 해당한다. */
    private fun read(
        rows: List<Row>,
        cursor: String?,
        limit: Int,
    ): Page {
        val after = CursorCodec.decode(SaveCursorSpec, cursor, condition)
        val ordered =
            rows
                .sortedWith(compareByDescending<Row> { it.updatedAt }.thenByDescending { it.saveId })
                .filter { after == null || it.key() < after }
        val page = ordered.take(limit)
        val nextCursor =
            if (ordered.size > limit) CursorCodec.encode(SaveCursorSpec, page.last().key(), condition) else null
        return Page(page.map { it.saveId }, nextCursor)
    }

    private operator fun SaveKey.compareTo(other: SaveKey): Int =
        compareValuesBy(this, other, { it.updatedAt }, { it.saveId })

    private fun row(
        saveId: Long,
        updatedAt: String,
    ) = Row(saveId, Instant.parse(updatedAt))

    private data class Row(
        val saveId: Long,
        val updatedAt: Instant,
    ) {
        fun key() = SaveKey(updatedAt, saveId)
    }

    private data class Page(
        val ids: List<Long>,
        val nextCursor: String?,
    )
}
