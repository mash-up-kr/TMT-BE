package com.tmt.input.http.mock

import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CurationTagMockControllerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(CurationTagMockController())
            .build()

    @Test
    fun `큐레이션 칩 목록을 명세 v2 계약 그대로 내린다`() {
        // 명세 v2 — B. 근처 탐색 §2-4의 응답 예시와 자구 단위로 일치해야 한다
        mockMvc
            .perform(get("/v1/curation-tags"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                      "items": [
                        { "curationTagId": "curation_euljiro_yajang", "label": "을지로 야장" },
                        { "curationTagId": "curation_ganmaek",        "label": "간맥집" },
                        { "curationTagId": "curation_butteotteok",    "label": "버터떡 카페" },
                        { "curationTagId": "curation_lamb",           "label": "양갈비" }
                      ]
                    }
                    """.trimIndent(),
                    JsonCompareMode.STRICT,
                ),
            )
    }
}
