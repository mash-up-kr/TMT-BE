package com.tmt.input.http.controller.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExceptionResponse(
    @field:Schema(description = "에러 코드", example = "NOT_FOUND_RESOURCE")
    val code: String?,
    @field:Schema(description = "에러 메시지", example = "요청 경로가 잘못되었습니다.")
    val message: String?,
    @field:Schema(description = "에러 원인 상세", nullable = true)
    val cause: String?,
    @field:Schema(description = "에러 발생 시각 (Unix timestamp, ms)", example = "1711900000000")
    val timestamp: Long,
)
