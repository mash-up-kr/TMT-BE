package com.tmt.input.http.mock

import com.tmt.application.domain.media.MediaRules

/**
 * 리뷰 폼 제약과 태그 풀 — 명세 v2 F §3-1의 서버 상수.
 * review-form-config 응답과 작성 완료(C4) 판정이 같은 값을 봐야 하므로 한 곳에 둔다.
 */
object ReviewFormRules {
    const val PHOTO_MAX_COUNT = 3
    const val PHOTO_MAX_BYTES = MediaRules.MAX_CONTENT_LENGTH
    val ALLOWED_CONTENT_TYPES = MediaRules.ALLOWED_CONTENT_TYPES.toList()

    const val RATING_MIN = 1
    const val RATING_MAX = 5
    const val RATING_STEP = 1

    const val CONTENT_MAX_LENGTH = 500

    data class TagDefinition(
        val tagId: String,
        val label: String,
    )

    val COMPANION_TAGS =
        listOf(
            TagDefinition("tag_alone", "혼자"),
            TagDefinition("tag_couple", "연인"),
            TagDefinition("tag_friend", "친구"),
            TagDefinition("tag_colleague", "동료·지인"),
            TagDefinition("tag_family", "가족"),
        )

    val POSITIVE_POINT_TAGS =
        listOf(
            TagDefinition("tag_tasty", "음식이 맛있어요"),
            TagDefinition("tag_kind", "응대가 친절해요"),
            TagDefinition("tag_mood", "분위기가 좋아요"),
            TagDefinition("tag_value", "가성비가 좋아요"),
            TagDefinition("tag_clean", "청결하고 깔끔해요"),
            TagDefinition("tag_transit", "교통이 편리해요"),
            TagDefinition("tag_spacious", "자리가 넓고 편해요"),
        )

    val COMPANION_TAG_IDS = COMPANION_TAGS.map { it.tagId }.toSet()
    val POSITIVE_POINT_TAG_IDS = POSITIVE_POINT_TAGS.map { it.tagId }.toSet()

    private val LABEL_BY_TAG_ID = (COMPANION_TAGS + POSITIVE_POINT_TAGS).associateBy({ it.tagId }, { it.label })

    fun labelOf(tagId: String): String = LABEL_BY_TAG_ID.getValue(tagId)

    /** 음식 카테고리 14종 (E11) — categoryId → 화면 노출명 */
    val FOOD_CATEGORIES =
        linkedMapOf(
            "cat_korean" to "한식",
            "cat_bunsik" to "분식",
            "cat_chinese" to "중식",
            "cat_japanese" to "일식",
            "cat_western" to "양식",
            "cat_asian" to "아시안",
            "cat_meat" to "고기·구이",
            "cat_seafood" to "해산물",
            "cat_cafe" to "카페·디저트",
            "cat_brunch" to "브런치",
            "cat_pub" to "주점",
            "cat_bar" to "바",
            "cat_fastfood" to "패스트푸드",
            "cat_buffet" to "뷔페",
        )
}
