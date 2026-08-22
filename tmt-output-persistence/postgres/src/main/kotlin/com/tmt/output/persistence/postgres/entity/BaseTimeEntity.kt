package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

/** `updated_at`이 있는 테이블만 상속한다 — users·place·save·groups 4종. */
@MappedSuperclass
abstract class BaseTimeEntity : BaseCreatedEntity() {
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set
}
