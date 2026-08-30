package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.DeleteSaveUseCase
import com.tmt.application.port.input.GetSaveUseCase
import com.tmt.application.port.input.IdempotentRequest
import com.tmt.application.port.input.IdempotentRequestUseCase
import com.tmt.application.port.input.ListMySavesUseCase
import com.tmt.application.port.input.MySaveKey
import com.tmt.application.port.input.MySavesRequest
import com.tmt.application.port.input.UpdateSaveCommand
import com.tmt.application.port.input.UpdateSaveUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import com.tmt.input.http.idempotency.IdempotencyKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

/**
 * 저장·리뷰 작성 실구현 (TMT-224 POST · TMT-225 나머지). 응답 형태·ID 표기는 mock과 같다.
 *
 * 매장 직접 등록(newPlace)은 TMT-193에서 이 핸들러에 얹는다.
 */
@Tag(name = "저장·리뷰 작성", description = "명세 v2 — F. 리뷰 작성 · G. 이어쓰기 · I. 리뷰 상세")
@RestController
@RequestMapping("/v1/saves")
class SaveController(
    private val createSaveUseCase: CreateSaveUseCase,
    private val updateSaveUseCase: UpdateSaveUseCase,
    private val deleteSaveUseCase: DeleteSaveUseCase,
    private val getSaveUseCase: GetSaveUseCase,
    private val listMySavesUseCase: ListMySavesUseCase,
    private val idempotentRequestUseCase: IdempotentRequestUseCase,
) {
    @Operation(
        summary = "작성 완료 (신규)",
        description = "완성도 판정(C4)을 충족하면 리뷰·티켓·매장 집계까지 같은 트랜잭션에서 나간다 (TX-1).",
    )
    @ApiErrorCodes(
        ErrorCode.PLACE_NOT_FOUND,
        ErrorCode.REVIEW_TAG_NOT_FOUND,
        ErrorCode.REVIEW_CONTENT_TOO_LONG,
        ErrorCode.MEDIA_NOT_OWNED,
        ErrorCode.MEDIA_ALREADY_ATTACHED,
        ErrorCode.IDEMPOTENCY_CONFLICT,
    )
    @PostMapping
    fun createSave(
        @UserId userId: Long,
        @IdempotencyKey key: String,
        @RequestBody request: SaveCreateRequest,
    ): ResponseEntity<SaveResultResponse> {
        val placeId = resolvePlaceId(request)
        val result =
            idempotentRequestUseCase.execute(
                IdempotentRequest(
                    userId = userId,
                    endpoint = ENDPOINT,
                    idemKey = key,
                    payload = request,
                    responseType = SaveResultResponse::class.java,
                    successStatus = 201,
                ),
            ) {
                val created =
                    createSaveUseCase.create(
                        CreateSaveCommand(
                            userId = userId,
                            placeId = placeId,
                            photoAssetIds = request.photoAssetIds.map(::parseAssetId),
                            companionTagIds = request.companionTagIds,
                            positivePointTagIds = request.positivePointTagIds,
                            rating = request.rating,
                            content = request.content,
                        ),
                    )
                SaveResultResponse(
                    saveId = PublicIds.save(created.saveId),
                    reviewId = created.reviewId?.let(PublicIds::review),
                    placeId = PublicIds.place(created.placeId),
                    ticket = SaveResultResponse.TicketGrantSummary(created.grantedCount, created.availableCount),
                )
            }
        return ResponseEntity
            .status(result.status)
            .location(URI.create("/v1/saves/${result.response.saveId}"))
            .body(result.response)
    }

    @Operation(
        summary = "작성 완료 (이어쓰기)",
        description = "전체 교체다. 매장은 바꿀 수 없다 (S6). 서버는 같은 완성도 판정을 다시 돌린다 (C6).",
    )
    @ApiErrorCodes(
        ErrorCode.SAVE_NOT_FOUND,
        ErrorCode.SAVE_ALREADY_REVIEWED,
        ErrorCode.SAVE_PLACE_IMMUTABLE,
        ErrorCode.REVIEW_TAG_NOT_FOUND,
        ErrorCode.REVIEW_CONTENT_TOO_LONG,
        ErrorCode.MEDIA_NOT_OWNED,
        ErrorCode.MEDIA_ALREADY_ATTACHED,
        ErrorCode.IDEMPOTENCY_CONFLICT,
    )
    @PutMapping("/{saveId}")
    fun updateSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
        @IdempotencyKey key: String,
        @RequestBody request: SaveCreateRequest,
    ): SaveResultResponse {
        val id = parseSaveId(saveId)
        return idempotentRequestUseCase
            .execute(
                IdempotentRequest(
                    userId = userId,
                    endpoint = "PUT /v1/saves/$saveId",
                    idemKey = key,
                    payload = request,
                    responseType = SaveResultResponse::class.java,
                    successStatus = 200,
                ),
            ) {
                val updated =
                    updateSaveUseCase.update(
                        UpdateSaveCommand(
                            userId = userId,
                            saveId = id,
                            // 접두가 어긋난 placeId도 "다른 매장"과 같게 본다 (S6)
                            placeId = request.placeId?.removePrefix("place_")?.toLongOrNull(),
                            newPlaceRequested = request.newPlace != null,
                            photoAssetIds = request.photoAssetIds.map(::parseAssetId),
                            companionTagIds = request.companionTagIds,
                            positivePointTagIds = request.positivePointTagIds,
                            rating = request.rating,
                            content = request.content,
                        ),
                    )
                SaveResultResponse(
                    saveId = PublicIds.save(updated.saveId),
                    reviewId = updated.reviewId?.let(PublicIds::review),
                    placeId = PublicIds.place(updated.placeId),
                    ticket = SaveResultResponse.TicketGrantSummary(updated.grantedCount, updated.availableCount),
                )
            }.response
    }

    @Operation(
        summary = "임시저장 버리기",
        description = "`새로 작성하기`가 이전 임시저장을 버린다. 리뷰가 된 저장은 DELETE /v1/reviews/{reviewId} 소관이다 (F·G·I §5-2).",
    )
    @ApiErrorCodes(ErrorCode.SAVE_NOT_FOUND, ErrorCode.SAVE_ALREADY_REVIEWED, ErrorCode.FORBIDDEN)
    @DeleteMapping("/{saveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
    ) {
        deleteSaveUseCase.delete(userId, parseSaveId(saveId))
    }

    @Operation(summary = "이어쓰기 목록", description = "미완성 저장만 내려간다 (C5·R8). 정렬은 updatedAt DESC, saveId DESC.")
    @GetMapping
    fun listSaves(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<SaveListItemResponse> {
        val condition = CursorCondition.of("MY_SAVES", userId)
        val result =
            listMySavesUseCase.list(
                MySavesRequest(
                    userId = userId,
                    after = CursorCodec.decode(MySaveCursorSpec, cursor, condition),
                    limit = PageLimit.of(limit),
                ),
            )
        val nextCursor =
            if (result.hasNext) result.lastKey?.let { CursorCodec.encode(MySaveCursorSpec, it, condition) } else null
        return CursorPage(
            items =
                result.items.map {
                    SaveListItemResponse(
                        saveId = PublicIds.save(it.saveId),
                        place =
                            SaveListItemResponse.PlaceAddressSummary(
                                placeId = PublicIds.place(it.placeId),
                                name = it.placeName,
                                roadAddress = it.placeRoadAddress,
                            ),
                        thumbnailUrl = it.thumbnailUrl,
                        updatedAt = it.updatedAt.toString(),
                    )
                },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    @Operation(summary = "본인 상세", description = "이어쓰기 재진입과 상세 시트를 모두 이걸로 그린다. 소유자에게만 응답한다 (S8).")
    @ApiErrorCodes(ErrorCode.SAVE_NOT_FOUND, ErrorCode.FORBIDDEN)
    @GetMapping("/{saveId}")
    fun getSave(
        @UserId userId: Long,
        @PathVariable saveId: String,
    ): SaveDetailResponse {
        val detail = getSaveUseCase.get(userId, parseSaveId(saveId))
        return SaveDetailResponse(
            saveId = PublicIds.save(detail.saveId),
            reviewId = detail.reviewId?.let(PublicIds::review),
            place =
                SaveDetailResponse.PlaceSummary(
                    placeId = PublicIds.place(detail.place.placeId),
                    name = detail.place.name,
                    roadAddress = detail.place.roadAddress,
                    categoryName = detail.place.categoryName,
                ),
            photos =
                detail.photos.map {
                    SaveDetailResponse.Photo(
                        photoId = PublicIds.savePhoto(it.photoId),
                        url = it.url,
                        order = it.order,
                    )
                },
            tags = detail.tags.map { SaveDetailResponse.TagResponse(it.tagId, it.label) },
            rating = detail.rating,
            content = detail.content,
            aiSummary = detail.aiSummary?.let { SaveDetailResponse.AiSummaryResponse(it.pros, it.cons) },
            createdAt = detail.createdAt.toString(),
        )
    }

    /** 접두·형식이 어긋나면 없는 저장과 같다. */
    private fun parseSaveId(publicId: String): Long =
        publicId.removePrefix("save_").toLongOrNull() ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)

    /** newPlace 경로는 아직 실구현이 없다 (TMT-193). placeId는 여전히 유일한 필수값이다 (C1). */
    private fun resolvePlaceId(request: SaveCreateRequest): Long {
        if (request.newPlace != null) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "매장 직접 등록은 아직 지원하지 않습니다.")
        }
        val placeId = request.placeId ?: throw TmtException(ErrorCode.VALIDATION_FAILED, "placeId는 필수입니다.")
        return PublicIds.parsePlaceId(placeId)
    }

    /** 발급 assetId는 숫자 문자열이다. 형식이 다르면 없는 사진과 같게 취급한다 (M2). */
    private fun parseAssetId(assetId: String): Long =
        assetId.toLongOrNull() ?: throw TmtException(ErrorCode.MEDIA_NOT_OWNED)

    data class SaveCreateRequest(
        val placeId: String? = null,
        val newPlace: NewPlaceRequest? = null,
        val photoAssetIds: List<String> = emptyList(),
        val companionTagIds: List<String> = emptyList(),
        val positivePointTagIds: List<String> = emptyList(),
        val rating: Int? = null,
        val content: String? = null,
    ) {
        data class NewPlaceRequest(
            val name: String,
            val addressId: String,
            val categoryId: String? = null,
        )
    }

    data class SaveResultResponse(
        val saveId: String,
        val reviewId: String?,
        val placeId: String,
        val ticket: TicketGrantSummary,
    ) {
        data class TicketGrantSummary(
            val grantedCount: Int,
            val availableCount: Int,
        )
    }

    data class SaveListItemResponse(
        val saveId: String,
        val place: PlaceAddressSummary,
        val thumbnailUrl: String?,
        val updatedAt: String,
    ) {
        data class PlaceAddressSummary(
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

        data class TagResponse(
            val tagId: String,
            val label: String,
        )

        data class AiSummaryResponse(
            val pros: String?,
            val cons: String?,
        )
    }

    /** (updatedAt, saveId) 내림차순 — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object MySaveCursorSpec : CursorSpec<MySaveKey> {
        override fun toKeys(key: MySaveKey) = listOf(key.updatedAt.toString(), key.saveId.toString())

        override fun fromKeys(keys: List<String>): MySaveKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return MySaveKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }

    companion object {
        /** 규약·DB PK가 (user_id, endpoint, idem_key)라 키 공간을 가르는 값이 필요하다. */
        private const val ENDPOINT = "POST /v1/saves"
    }
}
