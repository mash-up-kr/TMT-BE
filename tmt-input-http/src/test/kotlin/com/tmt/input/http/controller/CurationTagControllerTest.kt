package com.tmt.input.http.controller

import com.tmt.application.domain.place.CurationPresets
import com.tmt.application.domain.place.CurationTagService
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** 칩 목록은 서버 상수이고, 그 id가 그대로 지도·검색의 curationTagId 조건이 된다 (E12). */
class CurationTagControllerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(CurationTagController(CurationTagService()))
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `칩 목록이 mock과 같은 id·문구·순서로 나간다`() {
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
    fun `목록의 모든 칩이 검색 조건 프리셋을 가진다 — 빈 결과만 내는 칩이 없다`() {
        CurationPresets.BY_ID.forEach { (id, preset) ->
            check(preset.categoryId != null || preset.regionPrefix != null) { "$id 에 조건이 없다" }
        }
    }
}
