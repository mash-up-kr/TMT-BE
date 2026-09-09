package com.tmt.application.domain.save

import com.tmt.application.port.input.ReviewCriterion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveRulesTest {
    @Test
    fun `아무것도 없으면 네 항목이 선언 순서대로 모자란다 (C4·TMT-395)`() {
        val missing = SaveRules.missingReviewCriteria(0, 0, rating = null, content = null)

        assertEquals(ReviewCriterion.entries.toList(), missing)
    }

    @Test
    fun `공백뿐인 본문은 없는 본문이다 (C4 — 공백 제외 1자 이상)`() {
        val missing = SaveRules.missingReviewCriteria(1, 1, rating = 5, content = " \n ")

        assertEquals(listOf(ReviewCriterion.CONTENT), missing)
    }

    @Test
    fun `사진은 판정 항목이 아니다 — 전부 채우면 빈 목록이고 리뷰다 (C4-1)`() {
        val missing = SaveRules.missingReviewCriteria(1, 1, rating = 3, content = "맛")

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `모자란 항목만 골라낸다`() {
        val missing = SaveRules.missingReviewCriteria(2, 0, rating = null, content = "본문은 있다")

        assertEquals(listOf(ReviewCriterion.POSITIVE_POINT_TAG, ReviewCriterion.RATING), missing)
    }
}
