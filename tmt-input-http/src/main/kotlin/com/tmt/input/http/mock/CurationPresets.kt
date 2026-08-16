package com.tmt.input.http.mock

/** 칩은 서버 상수이고 검색 조건 프리셋으로 동작한다 (E12) — mock은 지역·카테고리 매칭으로 흉내낸다. */
object CurationPresets {
    private val PRESETS: Map<String, (MockPlace) -> Boolean> =
        mapOf(
            "curation_euljiro_yajang" to { p -> p.regionName.startsWith("중구") },
            "curation_ganmaek" to { p -> p.categoryName == "주점" },
            "curation_butteotteok" to { p -> p.categoryName == "카페·디저트" },
            "curation_lamb" to { p -> p.categoryName == "고기·구이" },
        )

    fun matcher(curationTagId: String): (MockPlace) -> Boolean = PRESETS[curationTagId] ?: { false }
}
