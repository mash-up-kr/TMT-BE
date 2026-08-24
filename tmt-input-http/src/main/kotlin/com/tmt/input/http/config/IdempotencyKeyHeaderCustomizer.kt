package com.tmt.input.http.config

import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.input.http.idempotency.IdempotencyKey
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod

/**
 * `@IdempotencyKey`는 커스텀 리졸버가 `Idempotency-Key` 헤더에서 읽는데, springdoc은 그 사실을 모르고
 * 파라미터 이름의 **쿼리 파라미터**로 문서화한다. 그 파라미터를 걷어내고 실제 계약인 헤더를 선언한다.
 *
 * 필수 여부는 `@IdempotencyKey key: String`(필수) / `@IdempotencyKey key: String?`(선택)을 그대로 따른다.
 */
class IdempotencyKeyHeaderCustomizer : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        val idemKey =
            handlerMethod.methodParameters.firstOrNull { it.hasParameterAnnotation(IdempotencyKey::class.java) }
                ?: return operation

        operation.parameters?.removeIf { it.`in` == QUERY && it.name == parameterNameOf(idemKey) }
        if (operation.parameters?.any { it.name == IdempotencyKeyArgumentResolver.HEADER } != true) {
            operation.addParametersItem(header(required = !idemKey.isOptional))
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
            .name(IdempotencyKeyArgumentResolver.HEADER)
            .required(required)
            .description("재시도가 중복을 만들지 않게 하는 키. 앞뒤 공백은 제거되고, 최대 100자다 (규약 §9-1)")
            .schema(
                StringSchema()
                    .maxLength(
                        IdempotencyRecord.IDEM_KEY_MAX_LENGTH,
                    ).example("6f7d9c8e-4b2a-4f1e-9c3d-2a7b5e8f1c04"),
            )

    companion object {
        private const val QUERY = "query"
        private const val DEFAULT_NAME = "key"
        private val NAME_DISCOVERER = DefaultParameterNameDiscoverer()
    }
}
