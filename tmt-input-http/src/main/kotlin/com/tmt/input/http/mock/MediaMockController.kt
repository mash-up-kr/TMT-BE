package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 업로드 대상 URL을 발급한다 (M1). mock에서는 실제 S3 대신 가짜 presigned URL을 내린다. */
@Tag(name = "미디어 (mock)", description = "명세 v2 — F §3-2")
@RestController
@RequestMapping("/v1/media/upload-intents")
class MediaMockController(
    private val mockAssetStore: InMemoryStore<MockAsset>,
) {
    @Operation(summary = "사진 업로드 presigned URL 발급", description = "발급받은 assetId는 발급받은 사용자만 Save에 붙일 수 있다 (M2).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUploadIntent(
        @UserId userId: Long,
        @RequestBody request: UploadIntentRequest,
    ): UploadIntentResponse {
        if (request.contentType !in ReviewFormRules.ALLOWED_CONTENT_TYPES) {
            throw TmtException(ErrorCode.MEDIA_CONTENT_TYPE_NOT_ALLOWED, "허용: ${ReviewFormRules.ALLOWED_CONTENT_TYPES}")
        }
        if (request.contentLength > ReviewFormRules.PHOTO_MAX_BYTES) {
            throw TmtException(ErrorCode.MEDIA_FILE_TOO_LARGE, "최대 ${ReviewFormRules.PHOTO_MAX_BYTES} bytes")
        }

        val asset =
            mockAssetStore.create { id ->
                MockAsset(
                    assetId = id,
                    ownerId = userId,
                    contentType = request.contentType,
                )
            }
        return UploadIntentResponse(
            assetId = asset.assetId,
            uploadUrl = "https://mock-upload.tmt.example/${asset.assetId}?signature=mock",
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).toString(),
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

/** Save 상세·카드의 사진 노출 URL — mock에서는 assetId로 결정되는 가짜 CDN 주소다. */
fun mockMediaUrl(assetId: String): String = "https://mock-cdn.tmt.example/$assetId.jpg"
