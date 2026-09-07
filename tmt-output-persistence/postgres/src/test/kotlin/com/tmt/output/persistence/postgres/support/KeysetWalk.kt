package com.tmt.output.persistence.postgres.support

import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 커서를 `limit = 1`로 끝까지 돌려 **중복도 누락도 없는지** 본다 (TMT-348).
 *
 * 키셋의 실패는 대개 정렬 키가 같은 행이 페이지 경계에 걸릴 때 나온다 — 술어가
 * `>` 대신 `>=`거나 tie-breaker가 빠지면, 같은 행이 두 페이지에 걸치거나(중복)
 * 경계 행이 통째로 건너뛰어진다(누락). `limit = 1`이면 **모든 행이 한 번씩 경계가 되므로**
 * 그 두 실패가 반드시 드러난다. 페이지 크기를 키우면 경계에 서는 행이 그만큼 줄어 놓친다.
 *
 * @param expected 나와야 하는 id를 정렬 순서대로
 * @param page 커서(직전 행) 하나를 받아 다음 한 행을 돌려준다. 첫 호출은 null이다
 */
fun <T> assertKeysetWalk(
    expected: List<Long>,
    idOf: (T) -> Long,
    page: (after: T?) -> List<T>,
) {
    val visited = mutableListOf<Long>()
    var cursor: T? = null
    // 무한 루프 방지 — 커서가 안 움직이면 같은 행을 영원히 돌려준다
    val maxSteps = expected.size + 1
    repeat(maxSteps) {
        val rows = page(cursor)
        if (rows.isEmpty()) {
            assertEquals(expected, visited, "커서 순회 결과가 다르다")
            return
        }
        val row = rows.first()
        val id = idOf(row)
        if (id in visited) fail("커서가 같은 행을 두 번 돌려줬다 - id=$id, 지금까지=$visited")
        visited += id
        cursor = row
    }
    fail("$maxSteps 번을 돌아도 끝나지 않았다 — 커서가 전진하지 않는다. 지금까지=$visited")
}
