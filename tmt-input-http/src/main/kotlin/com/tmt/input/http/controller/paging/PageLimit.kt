package com.tmt.input.http.controller.paging

/** 목록 요청의 `limit` — 기본 20, 상한 50 (규약 §5-2). */
object PageLimit {
    const val DEFAULT = 20
    const val MAX = 50

    /** 하한 1은 0·음수를 막는다 — 0이면 같은 커서가 무한히 반복된다. */
    fun of(limit: Int?): Int = (limit ?: DEFAULT).coerceIn(1, MAX)
}
