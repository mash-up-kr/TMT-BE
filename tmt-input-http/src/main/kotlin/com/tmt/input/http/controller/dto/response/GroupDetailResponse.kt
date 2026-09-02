package com.tmt.input.http.controller.dto.response

import com.tmt.application.domain.group.GroupTagCatalog
import com.tmt.application.port.input.GroupDetailView

/** 그룹 상세 (명세 v2 D_02 §3-1) — 생성·편집 응답과 상세 조회가 같은 형태를 쓴다. */
data class GroupDetailResponse(
    val groupId: String,
    val name: String,
    val oneLineDescription: String,
    val description: String?,
    val imageUrl: String?,
    val coverImages: List<CoverImage>,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val foodCategory: FoodCategory,
    val regionTags: List<RegionTag>,
    val matchedSavedPlaceCount: Int,
    val isMember: Boolean,
    val isOwner: Boolean,
) {
    data class CoverImage(
        val url: String,
        val reviewId: String,
    )

    data class FoodCategory(
        val categoryId: String,
        val label: String,
    )

    data class RegionTag(
        val regionTagId: String,
        val label: String,
    )
}

private val FOOD_LABELS = GroupTagCatalog.FOOD_CATEGORIES.associateBy({ it.id }, { it.label })
private val REGION_LABELS = GroupTagCatalog.REGION_TAGS.associateBy({ it.id }, { it.label })

fun GroupDetailView.toResponse(): GroupDetailResponse =
    GroupDetailResponse(
        groupId = PublicIds.group(groupId),
        name = name,
        oneLineDescription = oneLineDescription,
        description = description,
        imageUrl = imageUrl,
        coverImages = coverImages.map { GroupDetailResponse.CoverImage(it.url, PublicIds.review(it.reviewId)) },
        memberCount = memberCount,
        reviewCount = reviewCount,
        placeCount = placeCount,
        // 카탈로그에 없는 값이 DB에 있어도(시드 직삽입, 태그 폐기) 상세가 500이 되면 안 된다 —
        // 라벨만 id 그대로 못생기게 나가고 화면은 뜬다 (PR #80 리뷰)
        foodCategory = GroupDetailResponse.FoodCategory(foodCategoryId, FOOD_LABELS[foodCategoryId] ?: foodCategoryId),
        regionTags = regionTagIds.map { GroupDetailResponse.RegionTag(it, REGION_LABELS[it] ?: it) },
        matchedSavedPlaceCount = matchedSavedPlaceCount,
        isMember = isMember,
        isOwner = isOwner,
    )
