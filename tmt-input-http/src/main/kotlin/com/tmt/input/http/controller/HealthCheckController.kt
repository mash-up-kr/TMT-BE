package com.tmt.input.http.controller

import com.tmt.application.port.input.HealthCheckUseCase
import com.tmt.common.exception.ExceptionCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.controller.dto.response.ApiResponse
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
    fun apiHealth(): ApiResponse<HealthResponse> =
        ApiResponse.ok(
            HealthResponse(status = "UP", service = "tmt-api"),
        )

    @GetMapping("/db")
    fun dbHealth(): ResponseEntity<ApiResponse<HealthResponse>> {
        val isHealthy = healthCheckUseCase.checkDatabaseHealth()
        val httpStatus = if (isHealthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        val healthStatus = if (isHealthy) "UP" else "DOWN"
        return ResponseEntity
            .status(httpStatus)
            .body(
                ApiResponse(
                    status = httpStatus.value(),
                    message = httpStatus.reasonPhrase,
                    data = HealthResponse(status = healthStatus, service = "tmt-db"),
                ),
            )
    }

    @PostMapping("/error-test-global")
    fun errorTestGlobal(): Nothing = throw RuntimeException()

    @PostMapping("/error-test-tmt")
    fun errorTestTmt(): Nothing =
        throw TmtException(
            ExceptionCode.INTERNAL_ERROR_TEST,
            "error-test: 의도적으로 발생시킨 TmtException",
        )

    data class HealthResponse(
        val status: String,
        val service: String,
    )
}
