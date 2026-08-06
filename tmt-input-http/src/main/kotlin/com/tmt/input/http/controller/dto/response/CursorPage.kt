package com.tmt.input.http.controller.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "커서 기반 페이지 응답")
data class CursorPage<T>(
    @field:Schema(description = "결과 목록. 없으면 빈 배열")
    val items: List<T>,
    @field:Schema(description = "다음 페이지 요청에 그대로 전달. 마지막 페이지면 null", nullable = true)
    val nextCursor: String?,
    @field:Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
) {
    companion object {
        fun <T> of(
            items: List<T>,
            nextCursor: String?,
        ): CursorPage<T> = CursorPage(items = items, nextCursor = nextCursor, hasNext = nextCursor != null)
    }
}
