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

/**
 * 매장 검색어 판정 (E9) — 가게명·주소·음식 카테고리 태그를 본다.
 * 피드형 목록과 지도형 핀이 같은 술어를 써야 한 검색어에 두 모드가 다른 결과를 내지 않는다.
 */
fun MockPlace.matchesQuery(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        roadAddress.contains(query) ||
        categoryName?.contains(query) == true
