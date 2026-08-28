package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.IdempotentRequest
import com.tmt.application.port.input.IdempotentRequestUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.idempotency.IdempotencyKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 작성 완료 실구현 (TMT-224) — mock의 POST를 대체한다. 이어쓰기(PUT)·목록·상세는
 * TMT-225·226이 들어올 때까지 mock이 계속 응답한다.
 *
 * 매장 직접 등록(newPlace)은 TMT-193에서 이 핸들러에 얹는다.
 */
@Tag(name = "저장·리뷰 작성", description = "명세 v2 — F §4-1")
@RestController
@RequestMapping("/v1/saves")
class SaveController(
    private val createSaveUseCase: CreateSaveUseCase,
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

    companion object {
        /** 규약·DB PK가 (user_id, endpoint, idem_key)라 키 공간을 가르는 값이 필요하다. */
        private const val ENDPOINT = "POST /v1/saves"
    }
}
