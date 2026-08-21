package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 행이 없으면 응답의 aiSummary가 null이다. */
@Entity
@Table(name = "review_ai_summary")
class ReviewAiSummaryEntity(
    @Id
    val reviewId: Long,
    @Column(columnDefinition = "text")
    var pros: String? = null,
    @Column(columnDefinition = "text")
    var cons: String? = null,
    @Column(length = 50)
    var model: String? = null,
) : BaseCreatedEntity()
