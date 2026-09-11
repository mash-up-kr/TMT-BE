package com.tmt.application.port.input

import java.time.Instant

/**
 * 작성 완료 (F §4-1). 저장/리뷰 구분은 서버의 완성도 판정(C4)이 하고 클라이언트는 보내지 않는다 (C7).
 *
 * 예외가 하나 있다 — [CreateSaveCommand.draft]. 판정은 "무엇이 채워졌나"만 보므로 서버는
 * 사용자가 `작성 완료`를 눌렀는지 알 수 없고, 화면이 단계 사이에서 중간 저장을 하면 값이 다 찬
 * 순간 의도와 무관하게 리뷰로 확정돼 버린다. 그 의도만 클라이언트가 실어 보낸다 (TMT-426).
 */
interface CreateSaveUseCase {
    fun create(command: CreateSaveCommand): SaveResult
}

/**
 * 매장은 기존 것을 고르거나 직접 등록하거나 둘 중 하나다 — 정확히 하나만 온다 (C1, F §4-1).
 *
 * [New]는 **이미 해석이 끝난 값**이다. addressId 서명 검증과 좌표 API 호출은 트랜잭션 밖에서
 * 끝나 있어야 한다 — 외부 I/O를 트랜잭션에 넣지 않는다.
 */
sealed interface PlaceSelection {
    data class Existing(
        val placeId: Long,
    ) : PlaceSelection

    data class New(
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val regionName: String,
        val categoryId: String?,
        val latitude: Double,
        val longitude: Double,
    ) : PlaceSelection
}

/**
 * @param draft 중간 저장이다 — 판정(C4)을 충족해도 리뷰로 확정하지 않는다. 저장 자체는 똑같이
 *   이뤄지고 이어쓰기 목록에 남는다. 기본값은 지금까지의 동작(작성 완료)이다 (TMT-426).
 */
data class CreateSaveCommand(
    val userId: Long,
    val place: PlaceSelection,
    val photoAssetIds: List<Long>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
    val draft: Boolean = false,
)

/**
 * @param reviewId null이면 저장, 값이 있으면 리뷰다 (S3). 화면 분기의 유일한 기준.
 *   중간 저장(`draft`)은 판정을 충족해도 항상 null이다 — 이때만 `missing`이 비어 있으면서 null이다.
 * @param placeId newPlace로 만들어진 매장의 ID. 기존 매장이면 요청값과 같다.
 * @param grantedCount 이번 요청으로 발급된 티켓 수 (0 또는 1). 상한 999장이면 리뷰여도 0이다 (T6).
 * @param missing 리뷰 성립(C4)에 아직 모자란 항목. 리뷰가 됐으면 빈 목록이다 (TMT-395).
 */
data class SaveResult(
    val saveId: Long,
    val reviewId: Long?,
    val placeId: Long,
    val grantedCount: Int,
    val availableCount: Int,
    val missing: List<ReviewCriterion>,
)

/**
 * 리뷰 성립 판정(C4)의 항목. 응답 `missing`에 이름 그대로 실려 FE 안내 문구의 분기 기준이 되므로
 * ErrorCode처럼 **이름 변경은 파괴적 변경**이다 — 고치지 말고 추가한다 (TMT-395).
 * 사진은 판정 항목이 아니라서 없다 (C4-1).
 */
enum class ReviewCriterion {
    COMPANION_TAG,
    POSITIVE_POINT_TAG,
    RATING,
    CONTENT,
}

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
 * @param draft 중간 저장이다 — [CreateSaveCommand.draft]와 같은 뜻이고, 단계 사이의 자동 저장이
 *   실제로 반복해서 도는 자리는 이쪽이다 (TMT-426).
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
    val draft: Boolean = false,
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
