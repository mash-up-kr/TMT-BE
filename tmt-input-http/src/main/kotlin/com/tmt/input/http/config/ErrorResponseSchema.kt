package com.tmt.input.http.config

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "ErrorResponse", description = "실패 응답 (RFC 9457 Problem Details)")
data class ErrorResponseSchema(
    @field:Schema(description = "사용자에게 보여줄 수 있는 문구", example = "요청 경로가 잘못되었습니다.")
    val title: String,
    @field:Schema(description = "HTTP 상태 코드", example = "404")
    val status: Int,
    @field:Schema(description = "상황별 상세", nullable = true)
    val detail: String?,
    @field:Schema(description = "오류 식별자. 클라이언트 분기의 기준", example = "RESOURCE_NOT_FOUND")
    val code: String,
    @field:Schema(description = "발생 시각 (ISO-8601 UTC)", example = "2026-08-03T05:12:44.831Z")
    val timestamp: Instant,
    @field:Schema(description = "서버 로그 추적용", example = "8f1e0c4a-2c37-4d1e-9f6b-2f0a1c9d3e55")
    val requestId: String,
)
