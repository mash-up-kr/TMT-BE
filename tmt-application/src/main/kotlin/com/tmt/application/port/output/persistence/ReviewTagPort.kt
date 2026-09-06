package com.tmt.application.port.output.persistence

/** 태그 정의는 `review_tag_definition` 시드(V2)가 정본이다 — 동행 5 · 좋은 점 7. */
interface ReviewTagPort {
    /** 비활성 태그는 없는 것과 같다 (삭제 대신 비활성화). */
    fun findActiveTags(tagIds: Collection<String>): List<ReviewTagRow>

    /** 폼의 태그 칩 목록 (F §3-1) — 동행 먼저, 그 안에서 display_order 순. */
    fun findAllActiveDefinitions(): List<ReviewTagDefinitionRow>
}

data class ReviewTagDefinitionRow(
    val tagId: String,
    val label: String,
    val companion: Boolean,
)

data class ReviewTagRow(
    val tagId: String,
    val companion: Boolean,
)
