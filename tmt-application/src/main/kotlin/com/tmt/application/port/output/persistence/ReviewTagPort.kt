package com.tmt.application.port.output.persistence

/** 태그 정의는 `review_tag_definition` 시드(V2)가 정본이다 — 동행 5 · 좋은 점 7. */
interface ReviewTagPort {
    /** 비활성 태그는 없는 것과 같다 (삭제 대신 비활성화). */
    fun findActiveTags(tagIds: Collection<String>): List<ReviewTagRow>
}

data class ReviewTagRow(
    val tagId: String,
    val companion: Boolean,
)
