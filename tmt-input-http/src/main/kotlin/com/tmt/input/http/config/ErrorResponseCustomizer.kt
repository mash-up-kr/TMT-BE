package com.tmt.input.http.config

import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.exception.toHttpStatus
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.web.method.HandlerMethod

/**
 * 오퍼레이션마다 실패 응답을 선언한다. 바디는 components의 `ErrorResponse`를 참조한다.
 *
 * 핸들러 모양만 보고 알 수 있는 코드는 여기서 붙이고, 엔드포인트에서만 나는 코드는 [ApiErrorCodes]로 선언한다.
 * 컨트롤러가 이미 선언한 상태 코드는 덮지 않는다.
 */
class ErrorResponseCustomizer : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }

        errorCodesOf(operation, handlerMethod)
            .groupBy {
                it.errorType
                    .toHttpStatus()
                    .value()
                    .toString()
            }.filterKeys { responses[it] == null }
            .toSortedMap()
            .forEach { (status, codes) -> responses.addApiResponse(status, errorResponse(codes)) }

        return operation
    }

    private fun errorCodesOf(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Set<ErrorCode> =
        buildSet {
            add(ErrorCode.INTERNAL_ERROR)
            // 요청에서 값을 읽는 핸들러는 형식 오류로 400이 날 수 있다. 인증 헤더도 그 대상이다
            if (handlerMethod.methodParameters.isNotEmpty()) add(ErrorCode.VALIDATION_FAILED)
            if (requiresAuthentication(handlerMethod)) add(ErrorCode.UNAUTHORIZED)
            if (hasParameter(operation, CURSOR_PARAM)) add(ErrorCode.INVALID_CURSOR)
            if (hasParameter(operation, IDEMPOTENCY_KEY_HEADER)) add(ErrorCode.IDEMPOTENCY_CONFLICT)
            handlerMethod.getMethodAnnotation(ApiErrorCodes::class.java)?.value?.let(::addAll)
        }

    /** `@UserId Long`은 헤더가 없으면 401, `@UserId Long?`은 비로그인 열람을 허용한다. */
    private fun requiresAuthentication(handlerMethod: HandlerMethod): Boolean =
        handlerMethod.methodParameters.any {
            it.hasParameterAnnotation(UserId::class.java) && !it.isOptional
        }

    private fun hasParameter(
        operation: Operation,
        name: String,
    ): Boolean = operation.parameters?.any { it.name == name } == true

    private fun errorResponse(codes: List<ErrorCode>): ApiResponse =
        ApiResponse()
            .description(codes.joinToString("\n") { "- `${it.name}` — ${it.defaultMessage}" })
            .content(
                Content().addMediaType(
                    PROBLEM_JSON,
                    MediaType().schema(Schema<Any>().`$ref`(ERROR_RESPONSE_REF)),
                ),
            )

    companion object {
        const val ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse"

        /** ProblemDetail은 application/problem+json으로 나간다 */
        const val PROBLEM_JSON = "application/problem+json"

        private const val CURSOR_PARAM = "cursor"
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
