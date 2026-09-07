package com.tmt.input.http.controller

import com.tmt.application.port.input.HealthCheckUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthCheckController(
    private val healthCheckUseCase: HealthCheckUseCase,
) {
    @GetMapping("/api")
    fun apiHealth(): ResponseEntity<HealthResponse> =
        ResponseEntity.ok(HealthResponse(status = "UP", service = "tmt-api"))

    @ApiResponse(responseCode = "200", description = "DB 연결 정상")
    @ApiResponse(
        responseCode = "503",
        description = "DB 연결 실패",
        content = [Content(schema = Schema(implementation = HealthResponse::class))],
    )
    @GetMapping("/db")
    fun dbHealth(): ResponseEntity<HealthResponse> {
        val isHealthy = healthCheckUseCase.checkDatabaseHealth()
        return ResponseEntity
            .status(if (isHealthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE)
            .body(HealthResponse(status = if (isHealthy) "UP" else "DOWN", service = "tmt-db"))
    }

    /**
     * Sentry 수집 경로를 실제로 확인하는 자리다. 인증을 요구하는 이유는 노출 자체가 아니라
     * **이벤트 오염**이다 — 누구나 부를 수 있으면 부른 만큼 500이 이벤트로 쌓여 온콜 봇이
     * 그때마다 분석에 들어간다. 지우지 않는 것은 봇의 전 구간 검증에 계속 쓰기 때문이다 (TMT-354).
     */
    @PostMapping("/error-test-global")
    fun errorTestGlobal(
        @UserId userId: Long,
    ): Nothing = throw RuntimeException()

    @ApiErrorCodes(ErrorCode.INTERNAL_ERROR_TEST)
    @PostMapping("/error-test-tmt")
    fun errorTestTmt(
        @UserId userId: Long,
    ): Nothing =
        throw TmtException(
            ErrorCode.INTERNAL_ERROR_TEST,
            "error-test: 의도적으로 발생시킨 TmtException",
        )

    data class HealthResponse(
        val status: String,
        val service: String,
    )
}
