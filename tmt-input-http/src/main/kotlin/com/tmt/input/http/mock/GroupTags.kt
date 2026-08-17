package com.tmt.input.http.mock

/** 그룹 태그 풀 (D_02 §2-1) — 음식 14종(E11) + 지역 26종(서울 전체 + 25개 구, E10). 서버 상수라 페이징하지 않는다. */
object GroupTags {
    data class FoodCategory(
        val categoryId: String,
        val label: String,
    )

    data class RegionTag(
        val regionTagId: String,
        val label: String,
    )

    val FOOD_CATEGORIES: List<FoodCategory> =
        ReviewFormRules.FOOD_CATEGORIES.map { (id, label) ->
            FoodCategory(
                id,
                label,
            )
        }

    val REGION_TAGS: List<RegionTag> =
        listOf(
            RegionTag("region_seoul_all", "서울 전체"),
            RegionTag("region_gangnam", "강남구"),
            RegionTag("region_gangdong", "강동구"),
            RegionTag("region_gangbuk", "강북구"),
            RegionTag("region_gangseo", "강서구"),
            RegionTag("region_gwanak", "관악구"),
            RegionTag("region_gwangjin", "광진구"),
            RegionTag("region_guro", "구로구"),
            RegionTag("region_geumcheon", "금천구"),
            RegionTag("region_nowon", "노원구"),
            RegionTag("region_dobong", "도봉구"),
            RegionTag("region_dongdaemun", "동대문구"),
            RegionTag("region_dongjak", "동작구"),
            RegionTag("region_mapo", "마포구"),
            RegionTag("region_seodaemun", "서대문구"),
            RegionTag("region_seocho", "서초구"),
            RegionTag("region_seongdong", "성동구"),
            RegionTag("region_seongbuk", "성북구"),
            RegionTag("region_songpa", "송파구"),
            RegionTag("region_yangcheon", "양천구"),
            RegionTag("region_yeongdeungpo", "영등포구"),
            RegionTag("region_yongsan", "용산구"),
            RegionTag("region_eunpyeong", "은평구"),
            RegionTag("region_jongno", "종로구"),
            RegionTag("region_jung", "중구"),
            RegionTag("region_jungnang", "중랑구"),
        )

    val FOOD_CATEGORY_IDS = FOOD_CATEGORIES.map { it.categoryId }.toSet()
    val REGION_TAG_IDS = REGION_TAGS.map { it.regionTagId }.toSet()

    private val REGION_LABELS = REGION_TAGS.associateBy({ it.regionTagId }, { it.label })

    fun foodLabelOf(categoryId: String): String = ReviewFormRules.FOOD_CATEGORIES.getValue(categoryId)

    fun regionLabelOf(regionTagId: String): String = REGION_LABELS.getValue(regionTagId)
}
