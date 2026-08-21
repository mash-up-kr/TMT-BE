package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "place")
class PlaceEntity(
    @Column(length = 30, nullable = false)
    val externalSource: String,
    @Column(length = 100, nullable = false)
    val externalId: String,
    @Column(length = 100, nullable = false)
    var name: String,
    @Column(length = 200, nullable = false)
    var roadAddress: String,
    @Column(length = 200)
    var jibunAddress: String? = null,
    @Column(length = 50, nullable = false)
    var regionName: String,
    @Column(length = 30)
    var categoryId: String? = null,
    @Column(length = 20)
    var phoneNumber: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 갱신은 조건부 UPDATE로 한다 — 엔티티로 증감하면 동시 작성에서 lost update가 난다. */
    @Column(nullable = false)
    var reviewCount: Int = 0
        protected set

    @Column(nullable = false)
    var ratingSum: Long = 0
        protected set

    // location geography(Point,4326)은 매핑하지 않는다. 공간 조회는 네이티브 SQL로 쓴다.
}
