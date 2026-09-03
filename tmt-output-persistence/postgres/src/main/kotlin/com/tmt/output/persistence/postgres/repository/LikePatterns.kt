package com.tmt.output.persistence.postgres.repository

/**
 * 검색어를 LIKE/ILIKE 패턴으로 바꾼다 (TMT-296).
 *
 * 사용자 입력을 `'%' || :query || '%'`로 그대로 붙이면 `%`·`_`가 와일드카드로 동작해
 * 검색창에 `%`를 치면 전 행이 매칭된다. 여기서 메타문자를 이스케이프하고, 쿼리 쪽은
 * `ILIKE :pattern ESCAPE '\'`로 받는다 — 매장·그룹·근처 탐색이 같은 규칙을 쓴다 (E9·G18).
 */
object LikePatterns {
    private const val ESCAPE = '\\'
    private val META = setOf('\\', '%', '_')

    fun escape(raw: String): String =
        buildString(raw.length + 4) {
            for (c in raw) {
                if (c in META) append(ESCAPE)
                append(c)
            }
        }

    /** 부분 일치 패턴. null·빈 문자열은 검색 없음이라 그대로 null이다. */
    fun contains(raw: String?): String? = raw?.takeIf { it.isNotEmpty() }?.let { "%${escape(it)}%" }
}
