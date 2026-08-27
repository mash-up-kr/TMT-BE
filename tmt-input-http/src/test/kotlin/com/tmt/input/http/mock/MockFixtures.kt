package com.tmt.input.http.mock

import java.time.Instant

/** 탐색·가게·그룹 mock 테스트가 공유하는 픽스처 헬퍼. */
object MockFixtures {
    fun place(
        store: InMemoryStore<MockPlace>,
        name: String,
        latitude: Double = 37.5399,
        longitude: Double = 126.9515,
        categoryName: String? = "양식",
        regionName: String = "마포구 도화동",
        phoneNumber: String? = null,
    ): MockPlace =
        store.create { id ->
            MockPlace(id, name, "서울 마포구 도화동 200-14", regionName, categoryName, latitude, longitude, phoneNumber)
        }

    /** 완성된 리뷰(C4 충족) 상태의 저장을 만든다. */
    fun review(
        store: InMemoryStore<MockSave>,
        placeId: String,
        ownerId: Long = 1,
        reviewId: String,
        rating: Int = 5,
        photoAssetIds: List<String> = listOf("asset_1"),
        createdAt: Instant = Instant.parse("2026-08-12T09:11:03.412Z"),
    ): MockSave =
        store.create { id ->
            MockSave(
                saveId = id,
                ownerId = ownerId,
                placeId = placeId,
                photoAssetIds = photoAssetIds,
                companionTagIds = listOf("tag_couple"),
                positivePointTagIds = listOf("tag_kind"),
                rating = rating,
                content = "맛있어요",
                reviewId = reviewId,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }

    /** InMemoryStore.create가 넘겨준 id로 그룹을 만든다. */
    fun group(
        groupId: String,
        name: String = "성수 커피 탐험대",
        ownerId: Long = 999,
    ): MockGroup =
        MockGroup(
            groupId = groupId,
            name = name,
            oneLineDescription = "조용히 커피 맛에 집중하는 사람들",
            description = null,
            imageAssetId = null,
            foodCategoryId = "cat_cafe",
            regionTagIds = listOf("region_seongdong"),
            ownerId = ownerId,
            createdAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
}
