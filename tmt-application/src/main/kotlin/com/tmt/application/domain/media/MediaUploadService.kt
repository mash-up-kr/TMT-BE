package com.tmt.application.domain.media

import com.tmt.application.port.input.CreateUploadIntentUseCase
import com.tmt.application.port.input.UploadIntent
import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.application.port.output.storage.MediaStoragePort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MediaUploadService(
    private val mediaAssetPort: MediaAssetPort,
    private val mediaStoragePort: MediaStoragePort,
) : CreateUploadIntentUseCase {
    override fun create(
        ownerId: Long,
        contentType: String,
        contentLength: Long,
    ): UploadIntent {
        val extension =
            MediaRules.EXTENSION_BY_CONTENT_TYPE[contentType]
                ?: throw TmtException(
                    ErrorCode.MEDIA_CONTENT_TYPE_NOT_ALLOWED,
                    "허용: ${MediaRules.ALLOWED_CONTENT_TYPES}",
                )
        if (contentLength <= 0 || contentLength > MediaRules.MAX_CONTENT_LENGTH) {
            throw TmtException(ErrorCode.MEDIA_FILE_TOO_LARGE, "1~${MediaRules.MAX_CONTENT_LENGTH} bytes")
        }

        // UUID 키 — 버킷이 공개 읽기라 순번 키면 전량 열거가 가능해진다 (M2, media.tf 주석)
        val s3Key = "review/${UUID.randomUUID()}.$extension"
        val assetId = mediaAssetPort.createStaged(ownerId, s3Key, contentType, contentLength)
        val presigned = mediaStoragePort.presignPut(s3Key, contentType, contentLength)

        return UploadIntent(assetId = assetId, uploadUrl = presigned.url, expiresAt = presigned.expiresAt)
    }
}
