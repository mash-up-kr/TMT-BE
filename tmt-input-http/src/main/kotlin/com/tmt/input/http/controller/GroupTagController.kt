package com.tmt.input.http.controller

import com.tmt.application.domain.group.GroupTagCatalog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 그룹 태그 풀 실구현 (TMT-220) — 서버 상수라 저장소가 없다 (D4). */
@Tag(name = "그룹 태그 (mock)", description = "명세 v2 — D_02 §2-1")
@RestController
@RequestMapping("/v1/group-tags")
class GroupTagController {
    @Operation(summary = "그룹 태그 풀", description = "음식 카테고리는 단일 선택, 지역은 다중 선택이다 (G7). 서버 상수라 페이징하지 않는다.")
    @GetMapping
    fun groupTags(): GroupTagsResponse = RESPONSE

    data class GroupTagsResponse(
        val foodCategories: List<FoodCategory>,
        val regionTags: List<RegionTag>,
    )

    data class FoodCategory(
        val categoryId: String,
        val label: String,
    )

    data class RegionTag(
        val regionTagId: String,
        val label: String,
    )

    companion object {
        private val RESPONSE =
            GroupTagsResponse(
                foodCategories = GroupTagCatalog.FOOD_CATEGORIES.map { FoodCategory(it.id, it.label) },
                regionTags = GroupTagCatalog.REGION_TAGS.map { RegionTag(it.id, it.label) },
            )
    }
}
