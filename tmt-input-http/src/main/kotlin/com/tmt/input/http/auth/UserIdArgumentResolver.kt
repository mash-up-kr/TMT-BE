package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * `@UserId`를 인증 주체로 해석한다 — [AuthTokenFilter]가 검증한 토큰의 사용자 ID를
 * 요청 속성에서 읽는다 (TMT-272). X-User-Id 헤더 스텁(TMT-150)은 제거됐다.
 * `@UserId Long`(필수)인데 속성이 없으면 401, `@UserId Long?`(선택)이면 null이다.
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
        val userId = webRequest.getAttribute(USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? Long
        if (userId == null && !parameter.isOptional) throw TmtException(ErrorCode.UNAUTHORIZED)
        return userId
    }

    companion object {
        /** [AuthTokenFilter]가 검증 후 싣는다 */
        const val USER_ID_ATTRIBUTE = "tmt.auth.userId"
    }
}
