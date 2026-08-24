package com.tmt.input.http.idempotency

import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class IdempotencyKeyArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(IdempotencyKey::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): String? {
        val raw =
            webRequest.getHeader(HEADER)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return if (parameter.isOptional) {
                    null
                } else {
                    throw TmtException(ErrorCode.VALIDATION_FAILED, "$HEADER 헤더는 필수입니다.")
                }

        if (raw.length > IdempotencyRecord.IDEM_KEY_MAX_LENGTH) {
            throw TmtException(
                ErrorCode.VALIDATION_FAILED,
                "$HEADER 헤더는 최대 ${IdempotencyRecord.IDEM_KEY_MAX_LENGTH}자입니다.",
            )
        }
        return raw
    }

    companion object {
        const val HEADER = "Idempotency-Key"
    }
}
