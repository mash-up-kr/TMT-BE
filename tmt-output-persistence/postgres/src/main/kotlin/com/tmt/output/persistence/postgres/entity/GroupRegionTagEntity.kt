package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class GroupRegionTagId(
    @Column(nullable = false)
    val groupId: Long,
    @Column(length = 30, nullable = false)
    val regionTagId: String,
) : Serializable

@Entity
@Table(name = "group_region_tag")
class GroupRegionTagEntity(
    @EmbeddedId
    val id: GroupRegionTagId,
)
