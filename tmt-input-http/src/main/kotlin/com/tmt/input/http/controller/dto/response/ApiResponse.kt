package com.tmt.input.http.controller.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 API 응답 래퍼")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    @field:Schema(description = "HTTP 상태 코드", example = "200")
    val status: Int,
    @field:Schema(description = "응답 메시지", example = "OK")
    val message: String,
    @field:Schema(description = "응답 데이터 (없을 경우 필드 자체가 생략됨)")
    val data: T? = null,
) {
    companion object {
        fun ok(): ApiResponse<Unit> = ApiResponse(status = 200, message = "OK")

        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(status = 200, message = "OK", data = data)

        fun <T> created(data: T): ApiResponse<T> = ApiResponse(status = 201, message = "Created", data = data)

        fun <T> accepted(data: T): ApiResponse<T> = ApiResponse(status = 202, message = "Accepted", data = data)
    }
}
