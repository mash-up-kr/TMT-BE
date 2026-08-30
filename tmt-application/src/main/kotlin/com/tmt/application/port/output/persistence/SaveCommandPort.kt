package com.tmt.application.port.output.persistence

/** 저장·사진·태그·리뷰 쓰기. 호출부가 연 트랜잭션에 참여한다 (TX-1). */
interface SaveCommandPort {
    fun insertSave(
        userId: Long,
        placeId: Long,
        rating: Int?,
        content: String?,
    ): Long

    /** 배열 순서가 노출 순서다 — 인덱스를 photo_order로 쓴다. */
    fun insertPhotos(
        saveId: Long,
        assetIds: List<Long>,
    )

    fun insertTags(
        saveId: Long,
        tagIds: Collection<String>,
    )

    fun insertReview(
        saveId: Long,
        userId: Long,
        placeId: Long,
    ): Long
}
