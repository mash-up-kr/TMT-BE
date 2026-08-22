package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

/**
 * 작성 완료(F §4)·이어쓰기(G §5)·본인 상세(I §6-2).
 * 저장/리뷰 구분은 서버의 완성도 판정(C4)이 하고, 클라이언트는 보내지 않는다 (C7).
 */
@Tag(name = "저장·리뷰 작성 (mock)", description = "명세 v2 — F. 리뷰 작성 · G. 이어쓰기 · I. 리뷰 상세")
@RestController
@RequestMapping("/v1/saves")
class SaveMockController(
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockAssetStore: InMemoryStore<MockAsset>,
    private val mockTicketLedger: MockTicketLedger,
    private val mockReviewIdGenerator: MockReviewIdGenerator,
    private val mockIdempotencyRegistry: MockIdempotencyRegistry,
    private val mockAiSummaryStore: MockAiSummaryStore,
) {
    @Operation(summary = "작성 완료 (신규)", description = "저장이 생기고, 완성도 판정(C4)을 충족하면 리뷰와 티켓까지 같은 트랜잭션에서 나간다.")
    @PostMapping
    fun createSave(
        @UserId userId: Long,
        @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @RequestBody request: SaveRequest,
    ): ResponseEntity<SaveResultResponse> {
        val key = requireIdempotencyKey(idempotencyKey)
        replayed(userId, ENDPOINT_CREATE, key, request)?.let { return created(it) }

        validate(request, ownerId = userId, existingSave = null)

        val now = Instant.now()
        val completed = satisfiesReviewCriteria(request)
        val reviewId = if (completed) mockReviewIdGenerator.next() else null
        val save =
            mockSaveStore.create { id ->
                MockSave(
                    saveId = id,
                    ownerId = userId,
                    placeId = request.placeId,
                    photoAssetIds = request.photoAssetIds,
                    companionTagIds = request.companionTagIds,
                    positivePointTagIds = request.positivePointTagIds,
                    rating = request.rating,
                    content = request.content,
                    reviewId = reviewId,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        attachAssets(request.photoAssetIds)

        val granted = if (completed) mockTicketLedger.tryGrant(userId) else 0
        val result = toResult(save, granted, userId)
        mockIdempotencyRegistry.register(userId, ENDPOINT_CREATE, key, request.toString(), result)
        return created(result)
    }

    @Operation(summary = "작성 완료 (이어쓰기)", description = "전체 교체다. placeId는 바꿀 수 없고(S6), 서버는 같은 완성도 판정을 다시 돌린다 (C6).")
    @PutMapping("/{saveId}")
    fun updateSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
        @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @RequestBody request: SaveRequest,
    ): SaveResultResponse {
        val key = requireIdempotencyKey(idempotencyKey)
        // 재현 검사가 아래 reviewId 가드보다 앞에 있어야 한다 — 뒤로 가면 성공한 요청의
        // 재시도가 200 대신 409 SAVE_ALREADY_REVIEWED를 받는다 (규약 §9)
        replayed(userId, endpointUpdate(saveId), key, request)?.let { return it }

        val save = findOwned(saveId, userId)
        if (save.reviewId != null) {
            throw TmtException(ErrorCode.SAVE_ALREADY_REVIEWED)
        }
        if (request.placeId != save.placeId) {
            throw TmtException(ErrorCode.SAVE_PLACE_IMMUTABLE)
        }
        validate(request, ownerId = userId, existingSave = save)

        val completed = satisfiesReviewCriteria(request)
        val reviewId = if (completed) mockReviewIdGenerator.next() else null
        val updated =
            mockSaveStore.update(saveId) {
                it.copy(
                    photoAssetIds = request.photoAssetIds,
                    companionTagIds = request.companionTagIds,
                    positivePointTagIds = request.positivePointTagIds,
                    rating = request.rating,
                    content = request.content,
                    reviewId = reviewId,
                    updatedAt = Instant.now(),
                )
            } ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)
        detachAssets(save.photoAssetIds - request.photoAssetIds.toSet())
        attachAssets(request.photoAssetIds)

        val granted = if (completed) mockTicketLedger.tryGrant(userId) else 0
        val result = toResult(updated, granted, userId)
        mockIdempotencyRegistry.register(userId, endpointUpdate(saveId), key, request.toString(), result)
        return result
    }

    @Operation(summary = "이어쓰기 목록", description = "미완성 저장만 내려간다 (C5·R8). 정렬은 updatedAt DESC, saveId DESC.")
    @GetMapping
    fun listSaves(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<SaveListItemResponse> {
        val drafts =
            mockSaveStore
                .findAll()
                .filter { it.ownerId == userId && it.reviewId == null }
                .sortedWith(compareByDescending<MockSave> { it.updatedAt }.thenByDescending { it.saveId })
        return MockCursor.paginate(drafts, cursor, limit) { toListItem(it) }
    }

    @Operation(summary = "본인 상세", description = "이어쓰기 재진입과 상세 시트를 모두 이걸로 그린다. 소유자에게만 응답한다 (S8).")
    @GetMapping("/{saveId}")
    fun getSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
    ): SaveDetailResponse {
        val save = findOwned(saveId, userId)
        val place = mockPlaceStore.findById(save.placeId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)
        return SaveDetailResponse(
            saveId = save.saveId,
            reviewId = save.reviewId,
            place =
                SaveDetailResponse.PlaceSummary(
                    placeId = place.placeId,
                    name = place.name,
                    roadAddress = place.roadAddress,
                    categoryName = place.categoryName,
                ),
            photos =
                save.photoAssetIds.mapIndexed { index, assetId ->
                    SaveDetailResponse.Photo(
                        photoId = "sp_${assetId.removePrefix("asset_")}",
                        url = mockMediaUrl(assetId),
                        order = index,
                    )
                },
            tags =
                (save.companionTagIds + save.positivePointTagIds).map {
                    TagResponse(
                        it,
                        ReviewFormRules.labelOf(it),
                    )
                },
            rating = save.rating,
            content = save.content,
            // 리뷰가 아니거나 요약이 아직 생성되지 않았으면 null (A2)
            aiSummary = save.reviewId?.let { mockAiSummaryStore.find(it) }?.let { AiSummaryResponse(it.pros, it.cons) },
            createdAt = save.createdAt.toString(),
        )
    }

    private fun requireIdempotencyKey(key: String?): String =
        key?.takeIf { it.isNotBlank() }
            ?: throw TmtException(ErrorCode.VALIDATION_FAILED, "$IDEMPOTENCY_KEY_HEADER 헤더는 필수입니다.")

    /**
     * 같은 사용자·엔드포인트·키의 재요청이면 최초 응답을 그대로 돌려준다 (규약 §9).
     * 바디가 다르면 IDEMPOTENCY_CONFLICT다. 처음 보는 키면 null.
     */
    private fun replayed(
        userId: Long,
        endpoint: String,
        key: String,
        request: SaveRequest,
    ): SaveResultResponse? {
        val entry = mockIdempotencyRegistry.find(userId, endpoint, key) ?: return null
        if (entry.bodyFingerprint != request.toString()) {
            throw TmtException(ErrorCode.IDEMPOTENCY_CONFLICT)
        }
        return entry.response as SaveResultResponse
    }

    private fun findOwned(
        saveId: String,
        userId: Long,
    ): MockSave =
        mockSaveStore.findById(saveId)?.takeIf { it.ownerId == userId }
            ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)

    private fun validate(
        request: SaveRequest,
        ownerId: Long,
        existingSave: MockSave?,
    ) {
        mockPlaceStore.findById(request.placeId) ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)

        if (request.photoAssetIds.size > ReviewFormRules.PHOTO_MAX_COUNT) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "사진은 최대 ${ReviewFormRules.PHOTO_MAX_COUNT}장입니다.")
        }
        // DB가 save_photo.media_asset_id UNIQUE로 거부하는 자리다. mock이 더 관대하면
        // FE가 mock 기준으로 맞춰둔 것이 실구현 전환에서 깨진다.
        if (request.photoAssetIds.distinct().size != request.photoAssetIds.size) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "photoAssetIds에 같은 사진이 두 번 들어 있습니다.")
        }
        request.photoAssetIds.forEach { assetId ->
            val asset = mockAssetStore.findById(assetId)
            if (asset == null || asset.ownerId != ownerId) {
                throw TmtException(ErrorCode.MEDIA_NOT_OWNED)
            }
            val alreadyMine = existingSave?.photoAssetIds?.contains(assetId) == true
            if (asset.attached && !alreadyMine) {
                throw TmtException(ErrorCode.MEDIA_ALREADY_ATTACHED)
            }
        }

        (request.companionTagIds - ReviewFormRules.COMPANION_TAG_IDS).firstOrNull()?.let {
            throw TmtException(ErrorCode.REVIEW_TAG_NOT_FOUND, it)
        }
        (request.positivePointTagIds - ReviewFormRules.POSITIVE_POINT_TAG_IDS).firstOrNull()?.let {
            throw TmtException(ErrorCode.REVIEW_TAG_NOT_FOUND, it)
        }

        request.rating?.let {
            if (it !in ReviewFormRules.RATING_MIN..ReviewFormRules.RATING_MAX) {
                throw TmtException(ErrorCode.VALIDATION_FAILED, "rating은 1~5 정수입니다.")
            }
        }
        request.content?.let {
            if (it.length > ReviewFormRules.CONTENT_MAX_LENGTH) {
                throw TmtException(ErrorCode.REVIEW_CONTENT_TOO_LONG)
            }
        }
    }

    /** 리뷰 성립 판정 (C4) — 사진·동행 태그·좋은 점 태그·별점·본문(공백 제외 1자 이상)을 전부 충족해야 한다. */
    private fun satisfiesReviewCriteria(request: SaveRequest): Boolean =
        request.photoAssetIds.isNotEmpty() &&
            request.companionTagIds.isNotEmpty() &&
            request.positivePointTagIds.isNotEmpty() &&
            request.rating != null &&
            !request.content.isNullOrBlank()

    private fun attachAssets(assetIds: List<String>) =
        assetIds.forEach {
            mockAssetStore.update(it) { asset -> asset.copy(attached = true) }
        }

    private fun detachAssets(assetIds: Collection<String>) =
        assetIds.forEach { mockAssetStore.update(it) { asset -> asset.copy(attached = false) } }

    private fun created(body: SaveResultResponse): ResponseEntity<SaveResultResponse> =
        ResponseEntity.created(URI.create("/v1/saves/${body.saveId}")).body(body)

    private fun toResult(
        save: MockSave,
        grantedCount: Int,
        userId: Long,
    ): SaveResultResponse =
        SaveResultResponse(
            saveId = save.saveId,
            reviewId = save.reviewId,
            ticket = SaveResultResponse.TicketGrantSummary(grantedCount, mockTicketLedger.availableCount(userId)),
        )

    private fun toListItem(save: MockSave): SaveListItemResponse {
        val place = mockPlaceStore.findById(save.placeId)
        return SaveListItemResponse(
            saveId = save.saveId,
            place =
                SaveListItemResponse.PlaceSummary(
                    placeId = save.placeId,
                    name = place?.name ?: "(삭제된 매장)",
                    roadAddress = place?.roadAddress ?: "",
                ),
            thumbnailUrl = save.photoAssetIds.firstOrNull()?.let(::mockMediaUrl),
            updatedAt = save.updatedAt.toString(),
        )
    }

    data class SaveRequest(
        val placeId: String,
        val photoAssetIds: List<String> = emptyList(),
        val companionTagIds: List<String> = emptyList(),
        val positivePointTagIds: List<String> = emptyList(),
        val rating: Int? = null,
        val content: String? = null,
    )

    data class SaveResultResponse(
        val saveId: String,
        val reviewId: String?,
        val ticket: TicketGrantSummary,
    ) {
        data class TicketGrantSummary(
            val grantedCount: Int,
            val availableCount: Int,
        )
    }

    data class SaveListItemResponse(
        val saveId: String,
        val place: PlaceSummary,
        val thumbnailUrl: String?,
        val updatedAt: String,
    ) {
        data class PlaceSummary(
            val placeId: String,
            val name: String,
            val roadAddress: String,
        )
    }

    data class SaveDetailResponse(
        val saveId: String,
        val reviewId: String?,
        val place: PlaceSummary,
        val photos: List<Photo>,
        val tags: List<TagResponse>,
        val rating: Int?,
        val content: String?,
        val aiSummary: AiSummaryResponse?,
        val createdAt: String,
    ) {
        data class PlaceSummary(
            val placeId: String,
            val name: String,
            val roadAddress: String,
            val categoryName: String?,
        )

        data class Photo(
            val photoId: String,
            val url: String,
            val order: Int,
        )
    }

    data class TagResponse(
        val tagId: String,
        val label: String,
    )

    data class AiSummaryResponse(
        val pros: String?,
        val cons: String?,
    )

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

        // 멱등 등록부 키의 endpoint 성분 (규약·DB PK가 (user_id, endpoint, idem_key))
        private const val ENDPOINT_CREATE = "POST /v1/saves"

        private fun endpointUpdate(saveId: String) = "PUT /v1/saves/$saveId"
    }
}
