package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 카카오 로그인 도입 전까지 쓰는 인증 스텁 — X-User-Id 헤더 값을 사용자 ID로 신뢰한다.
 * 실인증이 들어오면 이 리졸버의 해석부만 토큰 검증으로 교체하고 컨트롤러는 그대로 둔다.
 */
class UserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(UserId::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long? {
        val raw =
            webRequest.getHeader(HEADER)
                ?: return if (parameter.isOptional) {
                    null
                } else {
                    throw TmtException(ErrorCode.UNAUTHORIZED)
                }

        return raw.toLongOrNull()
            ?: throw TmtException(ErrorCode.VALIDATION_FAILED, "$HEADER 헤더는 숫자여야 합니다.")
    }

    companion object {
        const val HEADER = "X-User-Id"
    }
}
