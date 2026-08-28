package com.tmt.application.domain.place

/**
 * 음식 카테고리 14종 (E11 · D4). 테이블이 아니라 서버 상수다 — 정본은 여기고,
 * mock의 `ReviewFormRules.FOOD_CATEGORIES`가 이 값을 참조한다.
 */
object FoodCategories {
    val LABEL_BY_ID: Map<String, String> =
        linkedMapOf(
            "cat_korean" to "한식",
            "cat_bunsik" to "분식",
            "cat_chinese" to "중식",
            "cat_japanese" to "일식",
            "cat_western" to "양식",
            "cat_asian" to "아시안",
            "cat_meat" to "고기·구이",
            "cat_seafood" to "해산물",
            "cat_cafe" to "카페·디저트",
            "cat_brunch" to "브런치",
            "cat_pub" to "주점",
            "cat_bar" to "바",
            "cat_fastfood" to "패스트푸드",
            "cat_buffet" to "뷔페",
        )

    fun labelOf(categoryId: String?): String? = categoryId?.let { LABEL_BY_ID[it] }
}

/**
 * 큐레이션 칩 (E12) — 검색 조건 프리셋으로 동작하는 서버 상수. 지역 전방일치와
 * 카테고리 매칭의 조합이다. 칩 목록·문구의 정본은 B 명세 §2-4.
 */
object CurationPresets {
    data class Preset(
        val categoryId: String? = null,
        val regionPrefix: String? = null,
    )

    val BY_ID: Map<String, Preset> =
        mapOf(
            "curation_euljiro_yajang" to Preset(regionPrefix = "중구"),
            "curation_ganmaek" to Preset(categoryId = "cat_pub"),
            "curation_butteotteok" to Preset(categoryId = "cat_cafe"),
            "curation_lamb" to Preset(categoryId = "cat_meat"),
        )
}
