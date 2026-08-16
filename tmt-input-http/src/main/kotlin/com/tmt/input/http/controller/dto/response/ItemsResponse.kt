package com.tmt.input.http.controller.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "페이징 없는 목록 응답")
data class ItemsResponse<T>(
    @field:Schema(description = "결과 목록. 없으면 빈 배열")
    val items: List<T>,
)
