package com.tmt.application.port.output.storage

import java.time.Instant

interface MediaStoragePort {
    /** 클라이언트가 직접 PUT 할 presigned URL (M1). contentType까지 서명에 묶는다. */
    fun presignPut(
        s3Key: String,
        contentType: String,
    ): PresignedUpload

    /** 객체 삭제 (M4 TTL 정리). 존재하지 않는 키는 조용히 지나간다 — S3 delete가 그렇다. */
    fun delete(s3Keys: Collection<String>)
}

data class PresignedUpload(
    val url: String,
    val expiresAt: Instant,
)
