package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "review")
class ReviewEntity(
    @Column(nullable = false, unique = true)
    val saveId: Long,
    @Column(nullable = false)
    val userId: Long,
    @Column(nullable = false)
    val placeId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    var deletedAt: Instant? = null
}
