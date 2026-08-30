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

    /** 이어쓰기의 본문·별점 교체. `updated_at`도 함께 올린다 — 이어쓰기 목록의 정렬 키다. */
    fun updateSave(
        saveId: Long,
        rating: Int?,
        content: String?,
    )

    /** 전체 교체·삭제 전에 부른다. 행이 남으면 media_asset_id UNIQUE가 재부착을 막는다. */
    fun deletePhotos(saveId: Long)

    fun deleteTags(saveId: Long)

    /** 조건부 UPDATE. 이미 지워진 저장이면 0 (D6). */
    fun softDeleteSave(saveId: Long): Int
}
