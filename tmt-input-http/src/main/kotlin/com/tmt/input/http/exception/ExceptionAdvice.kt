package com.tmt.input.http.exception

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.ErrorType
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import com.tmt.input.http.filter.RequestIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
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

    /**
     * 요청을 해석하지 못한 경우는 전부 400이다.
     *
     * 아래 셋은 Spring이 컨트롤러에 들어가기 **전에** 던진다 — 잡지 않으면 맨 아래 [handleException]이
     * 받아 **500 INTERNAL_ERROR**가 나간다. `?limit=abc` 하나로 서버 오류가 잡히던 자리다 (TMT-343).
     * 클라이언트 잘못을 서버 오류로 보고하면 알람·로그가 오염되고, FE는 재시도해도 되는 줄 안다.
     *
     * 메시지에 사용자가 보낸 **값을 넣지 않는다** — 무엇이 틀렸는지는 파라미터 이름으로 충분하고,
     * 값은 그대로 응답·로그에 실려 나가면 안 된다.
     */
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
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

                is MissingServletRequestParameterException -> "${e.parameterName}은(는) 필수입니다."

                // 기대 타입은 붙이지 않는다 — 같은 Int라도 nullable 여부로 `int`/`Integer`가 갈려
                // 메시지가 흔들리고, 정확한 타입은 스펙(/v3/api-docs)이 이미 알려준다
                is MethodArgumentTypeMismatchException -> "${e.name}의 형식이 올바르지 않습니다."

                // 본문 파싱 실패. 예외 메시지에 원본 조각이 섞여 나가므로 그대로 쓰지 않는다
                is HttpMessageNotReadableException -> "요청 본문을 읽을 수 없습니다."

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
