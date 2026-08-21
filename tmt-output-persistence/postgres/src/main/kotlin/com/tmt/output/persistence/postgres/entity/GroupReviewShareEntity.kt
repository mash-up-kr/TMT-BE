package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "group_review_share")
class GroupReviewShareEntity(
    @Column(nullable = false)
    val groupId: Long,
    @Column(nullable = false)
    val reviewId: Long,
    /** 공유자 = 리뷰 소유자. 집합 교체의 단위다. */
    @Column(nullable = false)
    val userId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
