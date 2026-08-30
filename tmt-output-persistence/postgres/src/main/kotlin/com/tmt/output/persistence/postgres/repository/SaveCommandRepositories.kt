package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.ReviewEntity
import com.tmt.output.persistence.postgres.entity.ReviewTagDefinitionEntity
import com.tmt.output.persistence.postgres.entity.SaveEntity
import com.tmt.output.persistence.postgres.entity.SavePhotoEntity
import com.tmt.output.persistence.postgres.entity.SaveTagEntity
import com.tmt.output.persistence.postgres.entity.SaveTagId
import org.springframework.data.jpa.repository.JpaRepository

interface SaveRepository : JpaRepository<SaveEntity, Long>

interface SavePhotoRepository : JpaRepository<SavePhotoEntity, Long>

interface SaveTagRepository : JpaRepository<SaveTagEntity, SaveTagId>

interface ReviewRepository : JpaRepository<ReviewEntity, Long>

interface ReviewTagDefinitionRepository : JpaRepository<ReviewTagDefinitionEntity, String> {
    fun findAllByIdInAndActiveIsTrue(ids: Collection<String>): List<ReviewTagDefinitionEntity>
}
