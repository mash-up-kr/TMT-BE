package com.tmt.application.port.output.persistence

/** 그룹 상세 조회 — 생성·편집 응답(TMT-221)과 상세 화면(TMT-222)이 같이 쓴다. */
interface GroupDetailPort {
    fun findDetail(
        groupId: Long,
        viewerId: Long?,
    ): GroupDetailRow?

    fun findRegionTagIds(groupId: Long): List<String>

    /** 공유 리뷰의 사진 — 리뷰 최신순, 리뷰 안에서는 photo_order (G16). */
    fun findCoverImages(
        groupId: Long,
        limit: Int,
    ): List<GroupCoverImageRow>
}

data class GroupDetailRow(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    val description: String?,
    val imageS3Key: String?,
    val ownerId: Long,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val foodCategoryId: String,
    val matchedSavedPlaceCount: Int,
    val isMember: Boolean,
)

data class GroupCoverImageRow(
    val s3Key: String,
    val reviewId: Long,
)
