package com.tmt.input.http.mock

import com.tmt.application.domain.group.GroupTagCatalog

/**
 * 그룹 태그 풀 (D_02 §2-1) — 정본은 [GroupTagCatalog]다 (TMT-220 실구현).
 * 남은 mock(그룹 생성·상세 등)이 검증·라벨 변환에 쓰는 동안만 위임 형태로 남는다.
 */
object GroupTags {
    data class FoodCategory(
        val categoryId: String,
        val label: String,
    )

    data class RegionTag(
        val regionTagId: String,
        val label: String,
    )

    val FOOD_CATEGORIES: List<FoodCategory> = GroupTagCatalog.FOOD_CATEGORIES.map { FoodCategory(it.id, it.label) }

    val REGION_TAGS: List<RegionTag> = GroupTagCatalog.REGION_TAGS.map { RegionTag(it.id, it.label) }

    val FOOD_CATEGORY_IDS = GroupTagCatalog.FOOD_CATEGORY_IDS

    val REGION_TAG_IDS = GroupTagCatalog.REGION_TAG_IDS

    private val REGION_LABELS = REGION_TAGS.associateBy({ it.regionTagId }, { it.label })

    private val FOOD_LABELS = FOOD_CATEGORIES.associateBy({ it.categoryId }, { it.label })

    fun foodLabelOf(categoryId: String): String = FOOD_LABELS.getValue(categoryId)

    fun regionLabelOf(regionTagId: String): String = REGION_LABELS.getValue(regionTagId)
}
