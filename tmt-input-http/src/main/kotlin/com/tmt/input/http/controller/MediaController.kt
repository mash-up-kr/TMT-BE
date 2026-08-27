package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateUploadIntentUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사진 업로드 presigned URL 발급 (M1) — mock을 대체하는 실구현 (TMT-202).
 * 응답 형태(assetId·uploadUrl·expiresAt)는 mock과 같아 FE 재생성이 필요 없다.
 *
 * 태그 문자열은 mock 시절 것을 그대로 둔다 — FE의 orval 생성 경로가 태그에 묶여 있어
 * (FE TMT-171) "(mock)"을 떼는 것만으로 생성 코드 경로가 바뀐다. TMT-171과 함께 정리한다.
 */
@Tag(name = "미디어 (mock)", description = "명세 v2 — F §3-2")
@RestController
@RequestMapping("/v1/media/upload-intents")
class MediaController(
    private val createUploadIntentUseCase: CreateUploadIntentUseCase,
) {
    @Operation(summary = "사진 업로드 presigned URL 발급", description = "발급받은 assetId는 발급받은 사용자만 Save에 붙일 수 있다 (M2).")
    @ApiErrorCodes(ErrorCode.MEDIA_CONTENT_TYPE_NOT_ALLOWED, ErrorCode.MEDIA_FILE_TOO_LARGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUploadIntent(
        @UserId userId: Long,
        @RequestBody request: UploadIntentRequest,
    ): UploadIntentResponse {
        val intent = createUploadIntentUseCase.create(userId, request.contentType, request.contentLength)
        return UploadIntentResponse(
            assetId = intent.assetId.toString(),
            uploadUrl = intent.uploadUrl,
            expiresAt = intent.expiresAt.toString(),
        )
    }

    data class UploadIntentRequest(
        val contentType: String,
        val contentLength: Long,
    )

    data class UploadIntentResponse(
        val assetId: String,
        val uploadUrl: String,
        val expiresAt: String,
    )
}
