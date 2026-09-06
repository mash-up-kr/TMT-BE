package com.tmt.application.port.output.persistence

import java.time.Instant

/** 저장 읽기 — 본인 상세(I §6-2)와 이어쓰기 목록(G §5-1). 삭제된 저장은 없는 것과 같다 (D6). */
interface SaveQueryPort {
    fun findSave(saveId: Long): SaveRow?

    fun findSavePhotos(saveId: Long): List<SavePhotoRow>

    fun findSaveTags(saveId: Long): List<SaveTagRow>

    /** 이어쓰기에서 이미 붙어 있던 사진 — 재부착 허용 판단과 교체 시 detach 대상이다 (M2). */
    fun findPhotoAssetIds(saveId: Long): List<Long>

    /** 미완성 저장만, (updated_at, id) 내림차순 키셋. */
    fun findMySaveRows(
        userId: Long,
        afterUpdatedAt: Instant?,
        afterSaveId: Long?,
        limit: Int,
    ): MySaveRows
}

/** @param reviewId null이면 저장, 값이 있으면 리뷰다 (S3). */
data class SaveRow(
    val saveId: Long,
    val userId: Long,
    val reviewId: Long?,
    val rating: Int?,
    val content: String?,
    val createdAt: Instant,
    val placeId: Long,
    val placeName: String,
    val placeRoadAddress: String,
    val placeCategoryId: String?,
    val aiSummaryPros: String?,
    val aiSummaryCons: String?,
)

data class SavePhotoRow(
    val savePhotoId: Long,
    val s3Key: String,
    val photoOrder: Int,
)

data class SaveTagRow(
    val tagId: String,
    val label: String,
)

data class MySaveRows(
    val rows: List<MySaveRow>,
    val hasNext: Boolean,
)

data class MySaveRow(
    val saveId: Long,
    val placeId: Long,
    val placeName: String,
    val placeRoadAddress: String,
    val thumbnailS3Key: String?,
    val updatedAt: Instant,
)
