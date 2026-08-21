package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "save_photo")
class SavePhotoEntity(
    @Column(nullable = false)
    val saveId: Long,
    @Column(nullable = false, unique = true)
    val mediaAssetId: Long,
    /** 0~2. save 하나당 유일하다. */
    @Column(nullable = false)
    val photoOrder: Short,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
