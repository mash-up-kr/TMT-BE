package com.tmt.input.http.exception

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.ErrorType
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import com.tmt.input.http.filter.RequestIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ExceptionAdvice {
    @ExceptionHandler(TmtException::class)
    fun handleTmtException(e: TmtException): ProblemDetail {
        if (e.errorCode.errorType == ErrorType.INTERNAL) {
            logger.error(e) { "TmtException 발생 - ${e.errorCode.name}" }
        } else {
            logger.warn { "TmtException 발생 - ${e.errorCode.name}: ${e.detailMessage ?: e.errorCode.defaultMessage}" }
        }
        return problemDetail(e.errorCode, e.detailMessage)
    }

    /** 티켓이 걸린 409는 화면 갱신용 티켓 상태를 함께 싣는다 (공통 규약 §3-2, I §6-4). */
    @ExceptionHandler(TicketShortageException::class)
    fun handleTicketShortage(e: TicketShortageException): ProblemDetail {
        logger.warn { "티켓 부족 - ${e.errorCode.name}: available=${e.availableCount}" }
        return problemDetail(e.errorCode, detail = null).apply {
            setProperty(
                "ticket",
                mapOf(
                    "requiredCount" to e.requiredCount,
                    "availableCount" to e.availableCount,
                    "shortageCount" to e.shortageCount,
                ),
            )
        }
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
    )
    fun handleValidationException(e: Exception): ProblemDetail {
        val detail =
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
        logger.warn { "Validation 실패 - $detail" }
        return problemDetail(ErrorCode.VALIDATION_FAILED, detail)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ProblemDetail {
        logger.warn { "리소스를 찾을 수 없음 - ${e.resourcePath}" }
        return problemDetail(ErrorCode.RESOURCE_NOT_FOUND, e.resourcePath)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ProblemDetail {
        logger.error(e) { "예기치 못한 에러 발생" }
        return problemDetail(ErrorCode.INTERNAL_ERROR, detail = null)
    }

    private fun problemDetail(
        errorCode: ErrorCode,
        detail: String?,
    ): ProblemDetail =
        ProblemDetail.forStatus(errorCode.errorType.toHttpStatus()).apply {
            title = errorCode.defaultMessage
            detail?.let { this.detail = it }
            setProperty("code", errorCode.name)
            setProperty("timestamp", Instant.now())
            RequestIdFilter.current()?.let { setProperty("requestId", it) }
        }
}
