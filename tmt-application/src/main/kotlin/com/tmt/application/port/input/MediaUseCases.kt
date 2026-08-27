package com.tmt.application.port.input

import java.time.Instant

/** 사진 업로드 presigned URL 발급 (M1). MediaAsset을 STAGED로 만들고 업로드 대상 URL을 돌려준다. */
interface CreateUploadIntentUseCase {
    fun create(
        ownerId: Long,
        contentType: String,
        contentLength: Long,
    ): UploadIntent
}

data class UploadIntent(
    val assetId: Long,
    val uploadUrl: String,
    val expiresAt: Instant,
)

/**
 * 사진을 Save에 붙이거나 뗀다 (M2·M3). 검증과 부착이 나뉜 것은 호출부(Save)가
 * 요청 검증 → 저장 생성 → 부착 순서로 진행하기 때문이다 — 실구현은 한 트랜잭션 안에서 부른다.
 */
interface AttachMediaUseCase {
    /**
     * 전부 [ownerId] 소유(M2, 아니면 MEDIA_NOT_OWNED 403)이고 부착 가능해야 한다.
     * 이미 붙은 사진은 MEDIA_ALREADY_ATTACHED(409) — 단, [reattachableIds]
     * (이어쓰기에서 이미 그 Save에 붙어 있던 사진)는 다시 붙일 수 있다.
     */
    fun verifyAttachable(
        ownerId: Long,
        assetIds: List<Long>,
        reattachableIds: Set<Long> = emptySet(),
    )

    /** STAGED → ATTACHED 조건부 전이. [reattachableIds]는 이미 ATTACHED라 건드리지 않는다. */
    fun attach(
        assetIds: List<Long>,
        reattachableIds: Set<Long> = emptySet(),
    )

    /** 이어쓰기에서 교체로 빠진 사진을 다시 STAGED로 되돌린다 — 재부착·TTL 정리(M4) 대상이 된다. */
    fun detach(assetIds: List<Long>)
}

/** 화면에 내보낼 사진 조회 URL — `base-url/s3_key` (공개 읽기 버킷, TMT-201). */
interface GetMediaUrlsUseCase {
    /** 존재하지 않는 id는 결과에서 빠진다. */
    fun urlsOf(assetIds: List<Long>): Map<Long, String>
}

/** 작성 중 이탈로 남은 STAGED 사진을 걷어낸다 (M4). ATTACHED는 건드리지 않는다. */
interface PurgeStagedMediaUseCase {
    fun purgeExpired(): Int
}
