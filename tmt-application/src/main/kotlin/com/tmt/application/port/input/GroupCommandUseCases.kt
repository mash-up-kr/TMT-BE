package com.tmt.application.port.input

/** 그룹 생성 (D_02 §2-2, TMT-221). 생성자는 자동으로 멤버가 된다 (G11·G13). */
fun interface CreateGroupUseCase {
    fun create(command: GroupCommand): GroupDetailView
}

/** 그룹 편집 (D_02 §4) — 생성자만, 전체 교체. */
fun interface UpdateGroupUseCase {
    fun update(
        groupId: Long,
        command: GroupCommand,
    ): GroupDetailView
}

data class GroupCommand(
    val requesterId: Long,
    val name: String,
    val oneLineDescription: String,
    val description: String?,
    val foodCategoryId: String,
    val regionTagIds: List<String>,
    val imageAssetId: Long?,
)

/** 그룹 상세 (D_02 §3-1) 읽기 모델 — 생성·편집 응답과 상세 조회(TMT-222)가 같은 형태를 쓴다. */
data class GroupDetailView(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    val description: String?,
    val imageUrl: String?,
    /** 공유 리뷰의 사진 — 리뷰 최신순 최대 5장 (G16). */
    val coverImages: List<CoverImage>,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val foodCategoryId: String,
    val regionTagIds: List<String>,
    val matchedSavedPlaceCount: Int,
    val isMember: Boolean,
    val isOwner: Boolean,
) {
    data class CoverImage(
        val url: String,
        val reviewId: Long,
    )
}
