package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class ReviewTagType {
    COMPANION,
    POSITIVE_POINT,
}

@Entity
@Table(name = "review_tag_definition")
class ReviewTagDefinitionEntity(
    /** 'tag_couple' — API의 tagId 그대로다. 서버가 부여하지 않는다. */
    @Id
    @Column(length = 30)
    val id: String,
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    val tagType: ReviewTagType,
    @Column(length = 30, nullable = false)
    var label: String,
    @Column(nullable = false)
    var displayOrder: Short,
    @Column(nullable = false)
    var active: Boolean = true,
)
