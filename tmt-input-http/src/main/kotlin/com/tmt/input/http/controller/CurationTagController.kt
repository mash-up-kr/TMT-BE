package com.tmt.input.http.controller

import com.tmt.application.port.input.GetCurationTagsUseCase
import com.tmt.input.http.controller.dto.response.ItemsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "큐레이션", description = "명세 v2 — B. 근처 탐색 §2-4")
@RestController
@RequestMapping("/v1/curation-tags")
class CurationTagController(
    private val getCurationTagsUseCase: GetCurationTagsUseCase,
) {
    @Operation(summary = "큐레이션 칩 목록", description = "칩 값은 서버 상수다. 페이징하지 않으며 값이 바뀌면 서버 배포로 반영된다.")
    @GetMapping
    fun curationTags(): ItemsResponse<CurationTagResponse> =
        ItemsResponse(getCurationTagsUseCase.get().map { CurationTagResponse(it.curationTagId, it.label) })

    @Schema(description = "큐레이션 칩")
    data class CurationTagResponse(
        @field:Schema(
            description = "칩 식별자. 검색(placesSearch)의 curationTagId로 그대로 전달",
            example = "curation_euljiro_yajang",
        )
        val curationTagId: String,
        @field:Schema(description = "화면 노출 문구", example = "을지로 야장")
        val label: String,
    )
}
