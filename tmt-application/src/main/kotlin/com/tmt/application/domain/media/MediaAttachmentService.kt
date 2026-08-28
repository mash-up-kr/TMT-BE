package com.tmt.application.domain.media

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.GetMediaUrlsUseCase
import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MediaAttachmentService(
    private val mediaAssetPort: MediaAssetPort,
    @param:Value("\${tmt.media.base-url:}") private val baseUrl: String,
) : AttachMediaUseCase,
    GetMediaUrlsUseCase {
    override fun verifyAttachable(
        ownerId: Long,
        assetIds: List<Long>,
        reattachableIds: Set<Long>,
    ) {
        if (assetIds.isEmpty()) return
        val found = mediaAssetPort.findByIds(assetIds).associateBy { it.id }
        assetIds.forEach { id ->
            val asset = found[id]
            // 없는 사진과 남의 사진을 구분해서 알려주지 않는다 — 존재 여부가 새는 것도 정보다
            if (asset == null || asset.ownerId != ownerId) {
                throw TmtException(ErrorCode.MEDIA_NOT_OWNED)
            }
            if (asset.attached && id !in reattachableIds) {
                throw TmtException(ErrorCode.MEDIA_ALREADY_ATTACHED)
            }
        }
    }

    override fun attach(
        assetIds: List<Long>,
        reattachableIds: Set<Long>,
    ) {
        val toAttach = assetIds.filterNot { it in reattachableIds }
        if (toAttach.isEmpty()) return
        val transitioned = mediaAssetPort.markAttached(toAttach)
        // 검증과 부착 사이에 다른 요청이 먼저 붙였다 — 조건부 UPDATE가 최종 심판이다 (TMT-177)
        if (transitioned != toAttach.size) {
            throw TmtException(ErrorCode.MEDIA_ALREADY_ATTACHED)
        }
    }

    override fun detach(assetIds: List<Long>) {
        if (assetIds.isEmpty()) return
        mediaAssetPort.markStaged(assetIds)
    }

    override fun urlsOf(assetIds: List<Long>): Map<Long, String> {
        if (assetIds.isEmpty()) return emptyMap()
        return mediaAssetPort
            .findByIds(assetIds)
            .associate { it.id to "${baseUrl.trimEnd('/')}/${it.s3Key}" }
    }
}
