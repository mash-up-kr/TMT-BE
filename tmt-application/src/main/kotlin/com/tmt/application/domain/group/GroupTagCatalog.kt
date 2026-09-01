package com.tmt.application.domain.group

import com.tmt.application.domain.place.FoodCategories

/**
 * 그룹 태그 풀 (D_01 §2-1) — 음식 14종(E11) + 지역 26종(서울 전체 + 25개 구, E10).
 * 서버 상수라 테이블이 없고, `group_region_tag.region_tag_id`가 이 코드를 참조한다 (D4).
 */
object GroupTagCatalog {
    data class Tag(
        val id: String,
        val label: String,
    )

    /** 음식 카테고리 — 매장 카테고리 매핑(TMT-162)과 같은 14종을 그대로 쓴다. */
    val FOOD_CATEGORIES: List<Tag> = FoodCategories.LABEL_BY_ID.map { (id, label) -> Tag(id, label) }

    val REGION_TAGS: List<Tag> =
        listOf(
            Tag("region_seoul_all", "서울 전체"),
            Tag("region_gangnam", "강남구"),
            Tag("region_gangdong", "강동구"),
            Tag("region_gangbuk", "강북구"),
            Tag("region_gangseo", "강서구"),
            Tag("region_gwanak", "관악구"),
            Tag("region_gwangjin", "광진구"),
            Tag("region_guro", "구로구"),
            Tag("region_geumcheon", "금천구"),
            Tag("region_nowon", "노원구"),
            Tag("region_dobong", "도봉구"),
            Tag("region_dongdaemun", "동대문구"),
            Tag("region_dongjak", "동작구"),
            Tag("region_mapo", "마포구"),
            Tag("region_seodaemun", "서대문구"),
            Tag("region_seocho", "서초구"),
            Tag("region_seongdong", "성동구"),
            Tag("region_seongbuk", "성북구"),
            Tag("region_songpa", "송파구"),
            Tag("region_yangcheon", "양천구"),
            Tag("region_yeongdeungpo", "영등포구"),
            Tag("region_yongsan", "용산구"),
            Tag("region_eunpyeong", "은평구"),
            Tag("region_jongno", "종로구"),
            Tag("region_jung", "중구"),
            Tag("region_jungnang", "중랑구"),
        )

    val FOOD_CATEGORY_IDS: Set<String> = FOOD_CATEGORIES.map { it.id }.toSet()
    val REGION_TAG_IDS: Set<String> = REGION_TAGS.map { it.id }.toSet()

    /** 검색어가 라벨에 부분 일치하는 태그 id — 그룹 검색은 태그 라벨도 대상이다 (G18). */
    fun foodIdsMatching(query: String): List<String> = FOOD_CATEGORIES.filter { it.label.contains(query) }.map { it.id }

    fun regionIdsMatching(query: String): List<String> = REGION_TAGS.filter { it.label.contains(query) }.map { it.id }
}
