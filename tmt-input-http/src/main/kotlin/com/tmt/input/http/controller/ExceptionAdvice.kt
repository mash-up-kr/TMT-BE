package com.tmt.input.http.controller

import com.tmt.common.exception.ExceptionCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.controller.dto.response.ExceptionResponse
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ExceptionAdvice {
    @ExceptionHandler(TmtException::class)
    fun handleTmtException(e: TmtException): ResponseEntity<ExceptionResponse> {
        if (e.statusCode in 500..599) {
            logger.error(e) { "TmtException 발생 (${e.statusCode})" }
        } else {
            logger.warn { "TmtException 발생 (${e.statusCode}) - ${e.defaultMessage}" }
        }
        return ResponseEntity
            .status(e.statusCode)
            .body(
                ExceptionResponse(
                    code = e.exceptionCode.name,
                    message = e.defaultMessage,
                    cause = e.detailMessage,
                    timestamp = System.currentTimeMillis(),
                ),
            )
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
    )
    fun handleValidationException(e: Exception): ResponseEntity<ExceptionResponse> {
        logger.warn { "Validation 실패 - ${e.message}" }

        val cause =
            when (e) {
                is MethodArgumentNotValidException ->
                    e.bindingResult.fieldErrors.joinToString(", ") {
                        "${it.field}: ${it.defaultMessage}"
                    }

                is ConstraintViolationException ->
                    e.constraintViolations.joinToString(", ") {
                        "${it.propertyPath}: ${it.message}"
                    }

                else -> e.message
            }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ExceptionResponse(
                    code = ExceptionCode.BAD_REQUEST_VALIDATION.name,
                    message = ExceptionCode.BAD_REQUEST_VALIDATION.defaultMessage,
                    cause = cause,
                    timestamp = System.currentTimeMillis(),
                ),
            )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<ExceptionResponse> {
        logger.warn { "리소스를 찾을 수 없음 - ${e.resourcePath}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ExceptionResponse(
                    code = ExceptionCode.NOT_FOUND_RESOURCE.name,
                    message = ExceptionCode.NOT_FOUND_RESOURCE.defaultMessage,
                    cause = e.resourcePath,
                    timestamp = System.currentTimeMillis(),
                ),
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ExceptionResponse> {
        logger.error(e) { "예기치 못한 에러 발생" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ExceptionResponse(
                    code = ExceptionCode.INTERNAL_SERVER_ERROR.name,
                    message = "예기치 못한 에러가 발생했습니다.",
                    cause = null,
                    timestamp = System.currentTimeMillis(),
                ),
            )
    }
}
