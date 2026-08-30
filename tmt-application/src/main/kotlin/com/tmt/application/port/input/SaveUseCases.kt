package com.tmt.application.port.input

import java.time.Instant

/**
 * 작성 완료 (F §4-1). 저장/리뷰 구분은 서버의 완성도 판정(C4)이 하고 클라이언트는 보내지 않는다 (C7).
 * 매장 직접 등록(newPlace) 경로는 TMT-193에서 이 위에 얹는다.
 */
interface CreateSaveUseCase {
    fun create(command: CreateSaveCommand): SaveResult
}

data class CreateSaveCommand(
    val userId: Long,
    val placeId: Long,
    val photoAssetIds: List<Long>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
)

/**
 * @param reviewId null이면 저장, 값이 있으면 리뷰다 (S3). 화면 분기의 유일한 기준.
 * @param grantedCount 이번 요청으로 발급된 티켓 수 (0 또는 1). 상한 999장이면 리뷰여도 0이다 (T6).
 */
data class SaveResult(
    val saveId: Long,
    val reviewId: Long?,
    val placeId: Long,
    val grantedCount: Int,
    val availableCount: Int,
)

/**
 * 이어쓰기 (G §5). 전체 교체이고, 서버는 완성도 판정(C4)을 다시 돌린다 (C6).
 * 리뷰가 된 저장은 이 경로로 고치지 않는다 (S4).
 */
interface UpdateSaveUseCase {
    fun update(command: UpdateSaveCommand): SaveResult
}

/**
 * @param placeId 저장의 매장과 같아야 한다. 다르거나 읽을 수 없으면 SAVE_PLACE_IMMUTABLE (S6).
 * @param newPlaceRequested 매장 직접 등록 요청이 실려 오면 그것도 매장 변경이다 (S6).
 */
data class UpdateSaveCommand(
    val userId: Long,
    val saveId: Long,
    val placeId: Long?,
    val newPlaceRequested: Boolean = false,
    val photoAssetIds: List<Long>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
)

/** 임시저장 버리기 (F·G·I §5-2). 리뷰가 된 저장은 리뷰 삭제 소관이다. */
interface DeleteSaveUseCase {
    fun delete(
        userId: Long,
        saveId: Long,
    )
}

/** 본인 상세 (I §6-2) — 이어쓰기 재진입과 상세 시트가 같은 응답을 쓴다. */
interface GetSaveUseCase {
    fun get(
        userId: Long,
        saveId: Long,
    ): SaveDetailView
}

data class SaveDetailView(
    val saveId: Long,
    val reviewId: Long?,
    val place: Place,
    val photos: List<Photo>,
    val tags: List<Tag>,
    val rating: Int?,
    val content: String?,
    val aiSummary: AiSummary?,
    val createdAt: Instant,
) {
    data class Place(
        val placeId: Long,
        val name: String,
        val roadAddress: String,
        val categoryName: String?,
    )

    data class Photo(
        val photoId: Long,
        val url: String,
        val order: Int,
    )

    data class Tag(
        val tagId: String,
        val label: String,
    )

    data class AiSummary(
        val pros: String?,
        val cons: String?,
    )
}

/** 이어쓰기 목록 (G §5-1) — 본인의 미완성 저장만, updatedAt DESC. */
interface ListMySavesUseCase {
    fun list(request: MySavesRequest): MySavesResult
}

data class MySavesRequest(
    val userId: Long,
    val after: MySaveKey?,
    val limit: Int,
)

/** (updatedAt, saveId) 내림차순. saveId가 tie-breaker다 (TMT-178). */
data class MySaveKey(
    val updatedAt: Instant,
    val saveId: Long,
)

data class MySavesResult(
    val items: List<MySaveView>,
    val hasNext: Boolean,
) {
    val lastKey: MySaveKey?
        get() = items.lastOrNull()?.let { MySaveKey(it.updatedAt, it.saveId) }
}

data class MySaveView(
    val saveId: Long,
    val placeId: Long,
    val placeName: String,
    val placeRoadAddress: String,
    val thumbnailUrl: String?,
    val updatedAt: Instant,
)

/** 리뷰 폼 제약·태그 목록 (F §3-1). 태그는 `review_tag_definition` 시드(V2)가 정본이다. */
interface GetReviewFormConfigUseCase {
    fun get(): ReviewFormConfigView
}

data class ReviewFormConfigView(
    val photoMaxCount: Int,
    val photoMaxBytes: Long,
    val allowedContentTypes: List<String>,
    val ratingMin: Int,
    val ratingMax: Int,
    val ratingStep: Int,
    val contentMaxLength: Int,
    val companionTags: List<TagDefinitionView>,
    val positivePointTags: List<TagDefinitionView>,
)

data class TagDefinitionView(
    val tagId: String,
    val label: String,
)
