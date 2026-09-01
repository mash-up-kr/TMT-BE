package com.tmt.input.http.config

import com.tmt.input.http.auth.UserId
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.QueryParameter
import org.junit.jupiter.api.Test
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserIdSecurityCustomizerTest {
    private val customizer = UserIdSecurityCustomizer()

    @Suppress("unused")
    private class FixtureController {
        fun required(
            @UserId userId: Long,
        ) = userId

        fun optional(
            @UserId userId: Long?,
        ) = userId

        fun none(query: String) = query
    }

    @Test
    fun `필수 @UserId는 잘못 문서화된 쿼리 파라미터를 걷어내고 bearerAuth를 요구한다`() {
        val operation = operationWithUserIdQueryParam()

        customizer.customize(operation, handlerMethod("required", Long::class.java))

        assertTrue(operation.parameters.none { it.name == "userId" })
        assertEquals(
            listOf(UserIdSecurityCustomizer.SECURITY_SCHEME),
            operation.security
                .single()
                .keys
                .toList(),
        )
    }

    @Test
    fun `선택 @UserId는 보안 요구를 붙이지 않는다 - 토큰 없이도 호출 가능한 계약이다`() {
        val operation = operationWithUserIdQueryParam()

        customizer.customize(operation, handlerMethod("optional", Long::class.javaObjectType))

        assertTrue(operation.parameters.none { it.name == "userId" })
        assertNull(operation.security)
    }

    @Test
    fun `@UserId가 없는 핸들러는 건드리지 않는다`() {
        val operation = operationWithUserIdQueryParam()

        customizer.customize(operation, handlerMethod("none", String::class.java))

        assertEquals(listOf("userId"), operation.parameters.map { it.name })
        assertNull(operation.security)
    }

    private fun operationWithUserIdQueryParam(): Operation =
        Operation().apply { addParametersItem(QueryParameter().name("userId")) }

    private fun handlerMethod(
        name: String,
        paramType: Class<*>,
    ): HandlerMethod =
        HandlerMethod(FixtureController(), FixtureController::class.java.getDeclaredMethod(name, paramType))
}
