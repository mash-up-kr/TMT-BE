package com.tmt.input.http.controller

import com.tmt.application.port.input.HealthCheckUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
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

    @GetMapping("/db")
    fun dbHealth(): ResponseEntity<HealthResponse> {
        val isHealthy = healthCheckUseCase.checkDatabaseHealth()
        return ResponseEntity
            .status(if (isHealthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE)
            .body(HealthResponse(status = if (isHealthy) "UP" else "DOWN", service = "tmt-db"))
    }

    @PostMapping("/error-test-global")
    fun errorTestGlobal(): Nothing = throw RuntimeException()

    @PostMapping("/error-test-tmt")
    fun errorTestTmt(): Nothing =
        throw TmtException(
            ErrorCode.INTERNAL_ERROR_TEST,
            "error-test: 의도적으로 발생시킨 TmtException",
        )

    data class HealthResponse(
        val status: String,
        val service: String,
    )
}
