package com.tmt.input.http.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "그룹 태그 (mock)", description = "명세 v2 — D_02 §2-1")
@RestController
@RequestMapping("/v1/group-tags")
class GroupTagMockController {
    @Operation(summary = "그룹 태그 풀", description = "음식 카테고리는 단일 선택, 지역은 다중 선택이다 (G7). 서버 상수라 페이징하지 않는다.")
    @GetMapping
    fun groupTags(): GroupTagsResponse = RESPONSE

    data class GroupTagsResponse(
        val foodCategories: List<GroupTags.FoodCategory>,
        val regionTags: List<GroupTags.RegionTag>,
    )

    companion object {
        private val RESPONSE =
            GroupTagsResponse(foodCategories = GroupTags.FOOD_CATEGORIES, regionTags = GroupTags.REGION_TAGS)
    }
}
