package com.tmt.input.http.controller

import com.tmt.application.port.input.HealthCheckUseCase
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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

    data class HealthResponse(
        val status: String,
        val service: String,
    )
}
