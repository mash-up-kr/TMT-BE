package com.tmt.input.http.config

import com.tmt.input.http.auth.UserId
import com.tmt.input.http.auth.UserIdArgumentResolver
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod

/**
 * `@UserId`는 커스텀 리졸버가 `X-User-Id` 헤더에서 읽는데, springdoc은 그 사실을 모르고
 * 같은 이름의 **쿼리 파라미터**로 문서화한다. 그 파라미터를 걷어내고 실제 계약인 헤더를 선언한다.
 *
 * 필수 여부는 `@UserId Long`(필수) / `@UserId Long?`(선택)을 그대로 따른다.
 */
class UserIdHeaderCustomizer : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        val userId =
            handlerMethod.methodParameters.firstOrNull { it.hasParameterAnnotation(UserId::class.java) }
                ?: return operation

        operation.parameters?.removeIf { it.`in` == QUERY && it.name == parameterNameOf(userId) }
        if (operation.parameters?.any { it.name == UserIdArgumentResolver.HEADER } != true) {
            operation.addParametersItem(header(required = !userId.isOptional))
        }
        return operation
    }

    /** 이름 탐색이 실패하면 springdoc이 쓰는 기본 이름으로 되돌린다. */
    private fun parameterNameOf(parameter: MethodParameter): String {
        parameter.initParameterNameDiscovery(NAME_DISCOVERER)
        return parameter.parameterName ?: DEFAULT_NAME
    }

    private fun header(required: Boolean) =
        HeaderParameter()
            .name(UserIdArgumentResolver.HEADER)
            .required(required)
            .description("인증 스텁 — 사용자 ID. 카카오 로그인 도입 시 `Authorization` 헤더로 교체된다")
            .schema(IntegerSchema().format("int64").example(1))

    companion object {
        private const val QUERY = "query"
        private const val DEFAULT_NAME = "userId"
        private val NAME_DISCOVERER = org.springframework.core.DefaultParameterNameDiscoverer()
    }
}
