package com.tmt.input.http.controller

import com.tmt.application.domain.place.CurationTagService
import com.tmt.application.port.output.persistence.CurationTagPort
import com.tmt.application.port.output.persistence.CurationTagRow
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 칩 목록의 id가 그대로 지도·검색의 `curationTagId` 조건이 된다 (E12).
 *
 * 값의 정본은 `curation_tag` 테이블이라(V9) 여기서는 **포트가 준 순서가 응답 순서로
 * 그대로 나가는지**만 본다 — 시드된 칩 4종과 정렬·active 필터는 `CurationTagAdapterTest`가 본다.
 */
class CurationTagControllerTest {
    private val port =
        object : CurationTagPort {
            override fun findActiveTags(): List<CurationTagRow> =
                listOf(
                    CurationTagRow("curation_euljiro_yajang", "을지로 야장"),
                    CurationTagRow("curation_ganmaek", "간맥집"),
                    CurationTagRow("curation_butteotteok", "버터떡 카페"),
                    CurationTagRow("curation_lamb", "양갈비"),
                )

            override fun existsActiveTag(curationTagId: String): Boolean = true
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(CurationTagController(CurationTagService(port)))
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `칩 목록이 포트가 준 id·문구·순서 그대로 나간다`() {
        mockMvc
            .perform(get("/v1/curation-tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[0].curationTagId").value("curation_euljiro_yajang"))
            .andExpect(jsonPath("$.items[0].label").value("을지로 야장"))
            .andExpect(jsonPath("$.items[1].curationTagId").value("curation_ganmaek"))
            .andExpect(jsonPath("$.items[2].curationTagId").value("curation_butteotteok"))
            .andExpect(jsonPath("$.items[3].label").value("양갈비"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `칩이 없으면 빈 목록이다 — 오류가 아니다`() {
        val emptyPort =
            object : CurationTagPort {
                override fun findActiveTags(): List<CurationTagRow> = emptyList()

                override fun existsActiveTag(curationTagId: String): Boolean = false
            }
        MockMvcBuilders
            .standaloneSetup(CurationTagController(CurationTagService(emptyPort)))
            .setControllerAdvice(ExceptionAdvice())
            .build()
            .perform(get("/v1/curation-tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }
}
