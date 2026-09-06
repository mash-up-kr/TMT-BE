package com.tmt.application.port.output.persistence

interface GroupCommandPort {
    /**
     * 그룹·지역 태그·생성자 멤버십을 만들고 groupId를 돌려준다.
     * 이름은 `groups.name` UNIQUE가 정본이다 — 경합 포함 위반이면 `GROUP_NAME_DUPLICATED`를 던진다 (G6).
     */
    fun create(
        ownerId: Long,
        name: String,
        oneLineDescription: String,
        description: String?,
        foodCategoryId: String,
        regionTagIds: List<String>,
        imageAssetId: Long?,
    ): Long

    /** 편집 대상의 소유자·현재 이미지 — 없으면 null (GROUP_NOT_FOUND). */
    fun findEditTarget(groupId: Long): GroupEditTarget?

    /** 전체 교체 (D_02 §4). 지역 태그는 집합 교체다. 이름 중복이면 `GROUP_NAME_DUPLICATED`. */
    fun update(
        groupId: Long,
        name: String,
        oneLineDescription: String,
        description: String?,
        foodCategoryId: String,
        regionTagIds: List<String>,
        imageAssetId: Long?,
    )
}

data class GroupEditTarget(
    val ownerId: Long,
    val imageAssetId: Long?,
)
