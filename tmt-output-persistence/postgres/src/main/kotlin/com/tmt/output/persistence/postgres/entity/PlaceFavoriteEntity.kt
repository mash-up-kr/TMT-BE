package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class PlaceFavoriteId(
    @Column(nullable = false)
    val userId: Long,
    @Column(nullable = false)
    val placeId: Long,
) : Serializable

@Entity
@Table(name = "place_favorite")
class PlaceFavoriteEntity(
    @EmbeddedId
    val id: PlaceFavoriteId,
) : BaseCreatedEntity()
