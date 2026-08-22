package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "save")
class SaveEntity(
    @Column(nullable = false)
    val userId: Long,
    @Column(nullable = false)
    val placeId: Long,
    /** 1~5. 컬럼이 smallint라 Short로 둔다 — Int면 validate가 타입 불일치로 기동을 막는다. */
    var rating: Short? = null,
    @Column(length = 500)
    var content: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 소프트 삭제는 조건부 UPDATE로 한다 — 읽고 쓰면 이미 삭제된 행의 시각을 덮어쓴다. */
    var deletedAt: Instant? = null
        protected set
}
