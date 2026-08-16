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
) {
    @Operation(summary = "작성 완료 (신규)", description = "저장이 생기고, 완성도 판정(C4)을 충족하면 리뷰와 티켓까지 같은 트랜잭션에서 나간다.")
    @PostMapping
    fun createSave(
        @UserId userId: Long,
        @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @RequestBody request: SaveRequest,
    ): ResponseEntity<SaveResultResponse> {
        val key = requireIdempotencyKey(idempotencyKey)

        mockIdempotencyRegistry.find(userId, key)?.let { entry ->
            if (entry.bodyFingerprint != request.toString()) {
                throw TmtException(ErrorCode.IDEMPOTENCY_CONFLICT)
            }
            val existing = mockSaveStore.findById(entry.saveId) ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)
            return created(toResult(existing, grantedCount = 0, userId = userId))
        }

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
        mockIdempotencyRegistry.register(userId, key, request.toString(), save.saveId)

        val granted = if (completed) mockTicketLedger.tryGrant(userId) else 0
        return created(toResult(save, granted, userId))
    }

    @Operation(summary = "작성 완료 (이어쓰기)", description = "전체 교체다. placeId는 바꿀 수 없고(S6), 서버는 같은 완성도 판정을 다시 돌린다 (C6).")
    @PutMapping("/{saveId}")
    fun updateSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
        @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @RequestBody request: SaveRequest,
    ): SaveResultResponse {
        requireIdempotencyKey(idempotencyKey)

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
        return toResult(updated, granted, userId)
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
            aiSummary =
                save.reviewId?.let {
                    // AI 요약은 별도 트랜잭션에서 생성된다 (A2) — mock은 리뷰가 되는 즉시 고정 요약을 내린다
                    AiSummaryResponse(pros = "분위기가 좋아요", cons = "가격이 좀 나가고 웨이팅이 많아요")
                },
            createdAt = save.createdAt.toString(),
        )
    }

    private fun requireIdempotencyKey(key: String?): String =
        key?.takeIf { it.isNotBlank() }
            ?: throw TmtException(ErrorCode.VALIDATION_FAILED, "$IDEMPOTENCY_KEY_HEADER 헤더는 필수입니다.")

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
            ticket = SaveResultResponse.TicketSummary(grantedCount, mockTicketLedger.availableCount(userId)),
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
        val ticket: TicketSummary,
    ) {
        data class TicketSummary(
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
    }
}
