package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewEntity
import com.tmt.output.persistence.postgres.entity.ReviewTagDefinitionEntity
import com.tmt.output.persistence.postgres.entity.SaveEntity
import com.tmt.output.persistence.postgres.entity.SavePhotoEntity
import com.tmt.output.persistence.postgres.entity.SaveTagEntity
import com.tmt.output.persistence.postgres.entity.SaveTagId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SaveRepository : JpaRepository<SaveEntity, Long> {
    /** 이어쓰기의 본문·별점 교체. updated_at은 이어쓰기 목록의 정렬 키라 함께 올린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SaveEntity s
        SET s.rating = :rating, s.content = :content, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.id = :saveId AND s.deletedAt IS NULL
        """,
    )
    fun updateContent(
        @Param("saveId") saveId: Long,
        @Param("rating") rating: Short?,
        @Param("content") content: String?,
    ): Int

    /** 임시저장 버리기는 행을 지운다 (F·G·I §5-2). 자식 행은 호출부가 먼저 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SaveEntity s WHERE s.id = :saveId")
    fun deleteRow(
        @Param("saveId") saveId: Long,
    ): Int

    /** 이미 지워진 행의 시각을 덮어쓰지 않게 WHERE에서 막는다 (D6). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SaveEntity s SET s.deletedAt = :now WHERE s.id = :saveId AND s.deletedAt IS NULL")
    fun softDelete(
        @Param("saveId") saveId: Long,
        @Param("now") now: Instant,
    ): Int
}

interface SavePhotoRepository : JpaRepository<SavePhotoEntity, Long> {
    @Query("SELECT p.mediaAssetId FROM SavePhotoEntity p WHERE p.saveId = :saveId")
    fun findMediaAssetIds(
        @Param("saveId") saveId: Long,
    ): List<Long>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SavePhotoEntity p WHERE p.saveId = :saveId")
    fun deleteBySaveId(
        @Param("saveId") saveId: Long,
    ): Int
}

interface SaveTagRepository : JpaRepository<SaveTagEntity, SaveTagId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SaveTagEntity t WHERE t.id.saveId = :saveId")
    fun deleteBySaveId(
        @Param("saveId") saveId: Long,
    ): Int
}

interface ReviewRepository : JpaRepository<ReviewEntity, Long>

interface ReviewTagDefinitionRepository : JpaRepository<ReviewTagDefinitionEntity, String> {
    fun findAllByIdInAndActiveIsTrue(ids: Collection<String>): List<ReviewTagDefinitionEntity>

    fun findAllByActiveIsTrueOrderByTagTypeAscDisplayOrderAsc(): List<ReviewTagDefinitionEntity>
}
