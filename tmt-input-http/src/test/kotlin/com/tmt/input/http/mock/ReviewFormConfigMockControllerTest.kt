package com.tmt.input.http.mock

import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ReviewFormConfigMockControllerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ReviewFormConfigMockController())
            .build()

    @Test
    fun `폼 제약과 태그 목록을 명세 v2 계약 그대로 내린다`() {
        // 명세 v2 — F §3-1의 응답 예시와 자구 단위로 일치해야 한다
        mockMvc
            .perform(get("/v1/review-form-config"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                      "photo": {
                        "maxCount": 3,
                        "maxBytes": 5242880,
                        "allowedContentTypes": ["image/jpeg", "image/png", "image/webp"]
                      },
                      "rating": { "min": 1, "max": 5, "step": 1 },
                      "content": { "maxLength": 500 },
                      "companionTags": [
                        { "tagId": "tag_alone",     "label": "혼자" },
                        { "tagId": "tag_couple",    "label": "연인" },
                        { "tagId": "tag_friend",    "label": "친구" },
                        { "tagId": "tag_colleague", "label": "동료·지인" },
                        { "tagId": "tag_family",    "label": "가족" }
                      ],
                      "positivePointTags": [
                        { "tagId": "tag_tasty",     "label": "음식이 맛있어요" },
                        { "tagId": "tag_kind",      "label": "응대가 친절해요" },
                        { "tagId": "tag_mood",      "label": "분위기가 좋아요" },
                        { "tagId": "tag_value",     "label": "가성비가 좋아요" },
                        { "tagId": "tag_clean",     "label": "청결하고 깔끔해요" },
                        { "tagId": "tag_transit",   "label": "교통이 편리해요" },
                        { "tagId": "tag_spacious",  "label": "자리가 넓고 편해요" }
                      ]
                    }
                    """.trimIndent(),
                    JsonCompareMode.STRICT,
                ),
            )
    }
}
