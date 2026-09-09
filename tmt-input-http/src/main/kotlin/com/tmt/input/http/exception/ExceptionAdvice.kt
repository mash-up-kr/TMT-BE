package com.tmt.input.http.exception

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.ErrorType
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import com.tmt.input.http.filter.RequestIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.apache.catalina.connector.ClientAbortException
import org.apache.tomcat.util.http.InvalidParameterException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ExceptionAdvice {
    @ExceptionHandler(TmtException::class)
    fun handleTmtException(e: TmtException): ProblemDetail {
        logByType(e.errorCode, e) {
            "[${e.errorCode.name}] ${e.detailMessage ?: e.errorCode.defaultMessage}"
        }
        return problemDetail(e.errorCode, e.detailMessage)
    }

    /** 티켓이 걸린 409는 화면 갱신용 티켓 상태를 함께 싣는다 (공통 규약 §3-2, I §6-4). */
    @ExceptionHandler(TicketShortageException::class)
    fun handleTicketShortage(e: TicketShortageException): ProblemDetail {
        logByType(e.errorCode, e) { "[${e.errorCode.name}] available=${e.availableCount}" }
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
     * 아래는 Spring·Tomcat이 컨트롤러에 들어가기 **전에** 던진다 — 잡지 않으면 맨 아래 [handleException]이
     * 받아 **500 INTERNAL_ERROR**가 나간다. `?limit=abc` 하나로 서버 오류가 잡히던 자리다 (TMT-343).
     * `?cursor=%%%`처럼 퍼센트 인코딩이 깨진 값은 Tomcat이 [InvalidParameterException]으로 던진다 (TMT-394).
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
        InvalidParameterException::class,
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

                // Tomcat 메시지에는 깨진 원본 값이 들어 있다 — 값은 응답·로그에 싣지 않는다 (LOGGING.md §4-5)
                is InvalidParameterException -> "요청 파라미터의 인코딩이 올바르지 않습니다."

                else -> e.message
            }
        logger.warn { "Validation 실패 - $detail" }
        return problemDetail(ErrorCode.VALIDATION_FAILED, detail)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ProblemDetail {
        // 없는 경로를 찔러본 것도 서버가 제 일을 한 결과다 (docs/LOGGING.md §3-1)
        logger.info { "리소스를 찾을 수 없음 - ${e.resourcePath}" }
        return problemDetail(ErrorCode.RESOURCE_NOT_FOUND, e.resourcePath)
    }

    /**
     * 클라이언트가 응답을 받다 연결을 끊은 경우다 (모바일 화면 이탈·새로고침). 서버가 고칠 것이 없고
     * 이미 나가던 응답이라 본문을 다시 쓸 수도 없는데, 맨 아래 [handleException]이 받으면
     * ERROR로 남아 Sentry 이벤트가 된다 — 온콜 봇이 조치할 수 없는 건을 매번 분석하게 된다 (TMT-354).
     *
     * 본문을 만들지 않는다 — 받을 상대가 이미 없다. 상태만 500으로 두는 것은 이 예외가
     * 비동기 타임아웃으로도 나기 때문이다. 그때는 연결이 살아 있어 빈 200이 나가면 안 된다.
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(AsyncRequestNotUsableException::class, ClientAbortException::class)
    fun handleClientAbort(e: Exception) {
        logger.warn { "클라이언트가 응답 수신 중 연결을 끊었다 - ${e.javaClass.simpleName}" }
    }

    /**
     * 요청 경로를 메시지에 싣는다 — 이 자리는 [ErrorCode]가 늘 `INTERNAL_ERROR`라
     * 넣지 않으면 서로 다른 엔드포인트의 500이 한 이슈로 묶인다. 쿼리스트링은 넣지 않는다 (§4-5).
     */
    @ExceptionHandler(Exception::class)
    fun handleException(
        e: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logger.error(e) { "예기치 못한 에러 발생 - ${request.method} ${request.requestURI}" }
        return problemDetail(ErrorCode.INTERNAL_ERROR, detail = null)
    }

    /**
     * 레벨은 [ErrorType] 하나로 정한다 — 자리마다 따로 정하면 기준이 흩어진다.
     * 5xx는 원인이 밖에 있어도 ERROR고, 4xx는 정상 클라이언트에서 나오는지로 갈린다
     * (docs/LOGGING.md §3-1).
     */
    private inline fun logByType(
        errorCode: ErrorCode,
        e: Throwable,
        crossinline message: () -> String,
    ) {
        when (errorCode.errorType) {
            ErrorType.INTERNAL, ErrorType.EXTERNAL_UNAVAILABLE, ErrorType.SERVICE_UNAVAILABLE ->
                logger.error(e) { message() }

            ErrorType.VALIDATION, ErrorType.RATE_LIMITED -> logger.warn { message() }

            // else를 두지 않는다 - 새 ErrorType이 들어오면 컴파일이 막아 레벨을 정하게 한다
            ErrorType.UNAUTHORIZED, ErrorType.FORBIDDEN, ErrorType.NOT_FOUND,
            ErrorType.CONFLICT, ErrorType.GONE, ErrorType.UNPROCESSABLE,
            -> logger.info { message() }
        }
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
