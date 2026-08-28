package com.tmt.application.port.output.storage

import java.time.Instant

interface MediaStoragePort {
    /**
     * 클라이언트가 직접 PUT 할 presigned URL (M1). contentType과 contentLength까지 서명에 묶는다 —
     * 서명 밖에 두면 발급 때 신고한 크기와 다른 파일을 올려도 통과해 5MiB 상한(M3)이 무력해진다.
     */
    fun presignPut(
        s3Key: String,
        contentType: String,
        contentLength: Long,
    ): PresignedUpload

    /** 객체 삭제 (M4 TTL 정리). 존재하지 않는 키는 조용히 지나간다 — S3 delete가 그렇다. */
    fun delete(s3Keys: Collection<String>)
}

data class PresignedUpload(
    val url: String,
    val expiresAt: Instant,
)
