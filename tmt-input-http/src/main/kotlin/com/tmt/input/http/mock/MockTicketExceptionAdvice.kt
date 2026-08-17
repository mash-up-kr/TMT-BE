package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.exception.toHttpStatus
import com.tmt.input.http.filter.RequestIdFilter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * 티켓이 걸린 409 응답은 화면 갱신용 티켓 상태를 함께 싣는다 (공통 규약 §3-2).
 * 리뷰 삭제(I §6-4)와 그룹 가입(H §2-2)이 같은 형태를 쓴다.
 */
open class TicketRequiredException(
    val errorCode: ErrorCode,
    val availableCount: Int,
    val requiredCount: Int = 1,
) : RuntimeException(errorCode.defaultMessage)

class ReviewDeleteTicketRequiredException(
    availableCount: Int,
) : TicketRequiredException(ErrorCode.REVIEW_DELETE_TICKET_REQUIRED, availableCount)

class GroupJoinTicketRequiredException(
    availableCount: Int,
) : TicketRequiredException(ErrorCode.GROUP_JOIN_TICKET_REQUIRED, availableCount)

// ExceptionAdvice의 Exception 캐치올보다 먼저 평가돼야 한다 — 어드바이스 간 우선순위는 @Order가 정한다
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class MockTicketExceptionAdvice {
    @ExceptionHandler(TicketRequiredException::class)
    fun handleTicketRequired(e: TicketRequiredException): ProblemDetail =
        ProblemDetail.forStatus(e.errorCode.errorType.toHttpStatus()).apply {
            title = e.errorCode.defaultMessage
            setProperty("code", e.errorCode.name)
            setProperty("timestamp", Instant.now())
            setProperty(
                "ticket",
                mapOf(
                    "requiredCount" to e.requiredCount,
                    "availableCount" to e.availableCount,
                    "shortageCount" to e.requiredCount - e.availableCount,
                ),
            )
            RequestIdFilter.current()?.let { setProperty("requestId", it) }
        }
}
