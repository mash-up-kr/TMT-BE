package com.tmt.input.http.config

import com.tmt.input.http.auth.UserId
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod

/**
 * `@UserId`는 [com.tmt.input.http.auth.AuthTokenFilter]가 `Authorization: Bearer`에서 읽는데,
 * springdoc은 그 사실을 모르고 같은 이름의 **쿼리 파라미터**로 문서화한다. 그 파라미터를 걷어내고
 * bearerAuth 보안 요구를 선언한다 (TMT-272 — X-User-Id 헤더 문서화를 대체).
 *
 * 선택(`@UserId Long?`)에는 보안 요구를 붙이지 않는다 — 토큰 없이도 호출 가능한 계약이다.
 */
class UserIdSecurityCustomizer : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        val userId =
            handlerMethod.methodParameters.firstOrNull { it.hasParameterAnnotation(UserId::class.java) }
                ?: return operation

        operation.parameters?.removeIf { it.`in` == QUERY && it.name == parameterNameOf(userId) }
        if (!userId.isOptional) {
            operation.addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME))
        }
        return operation
    }

    /** 이름 탐색이 실패하면 springdoc이 쓰는 기본 이름으로 되돌린다. */
    private fun parameterNameOf(parameter: MethodParameter): String {
        parameter.initParameterNameDiscovery(NAME_DISCOVERER)
        return parameter.parameterName ?: DEFAULT_NAME
    }

    companion object {
        const val SECURITY_SCHEME = "bearerAuth"
        private const val QUERY = "query"
        private const val DEFAULT_NAME = "userId"
        private val NAME_DISCOVERER = org.springframework.core.DefaultParameterNameDiscoverer()
    }
}
