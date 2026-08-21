package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "groups")
class GroupEntity(
    @Column(length = 50, nullable = false, unique = true)
    var name: String,
    @Column(length = 100, nullable = false)
    var oneLineDescription: String,
    @Column(length = 200)
    var description: String? = null,
    var imageAssetId: Long? = null,
    @Column(length = 30, nullable = false)
    var foodCategoryId: String,
    /** 생성자. 변경되지 않는다. */
    @Column(nullable = false)
    val ownerId: Long,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 갱신은 조건부 UPDATE로 한다 — 엔티티로 증감하면 동시 가입에서 lost update가 난다. */
    @Column(nullable = false)
    var memberCount: Int = 1
        protected set

    @Column(nullable = false)
    var reviewCount: Int = 0
        protected set

    @Column(nullable = false)
    var placeCount: Int = 0
        protected set
}
