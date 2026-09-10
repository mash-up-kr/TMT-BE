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

    /** 앞 일치 패턴. 술어가 아니라 정렬 가산점에 쓴다 (TMT-300). */
    fun startsWith(raw: String?): String? = raw?.takeIf { it.isNotEmpty() }?.let { "${escape(it)}%" }

    /**
     * 공백을 지운 부분 일치 패턴 (TMT-413). 쿼리 쪽도 `replace(name, ' ', '')`로 비교한다.
     *
     * `ILIKE '%검색어%'`는 글자가 그대로 이어져야 걸려서 **띄어쓰기 하나로 0건이 된다** —
     * 운영 데이터에서 `위드유 용산`이 `위드유용산카페`를, `오한수 우육면`이 `오한수우육면가`를
     * 못 찾았다. 공백을 양쪽에서 지우면 사용자가 어떻게 띄어 쓰든 걸린다.
     *
     * 공백을 먼저 지우고 이스케이프한다 — 순서가 뒤바뀌면 이스케이프 문자가 잘린다.
     */
    fun containsIgnoringSpaces(raw: String?): String? =
        raw
            ?.filterNot { it.isWhitespace() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { "%${escape(it)}%" }
}
