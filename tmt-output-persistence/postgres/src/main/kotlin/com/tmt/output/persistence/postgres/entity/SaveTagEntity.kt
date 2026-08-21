package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class SaveTagId(
    @Column(nullable = false)
    val saveId: Long,
    @Column(length = 30, nullable = false)
    val tagId: String,
) : Serializable

@Entity
@Table(name = "save_tag")
class SaveTagEntity(
    @EmbeddedId
    val id: SaveTagId,
)
