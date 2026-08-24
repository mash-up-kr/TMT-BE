package com.tmt.input.http.config

import com.tmt.input.http.idempotency.IdempotencyKey
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import org.junit.jupiter.api.Test
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdempotencyKeyHeaderCustomizerTest {
    private val customizer = IdempotencyKeyHeaderCustomizer()

    @Suppress("UNUSED_PARAMETER")
    class SampleController {
        fun required(
            @IdempotencyKey key: String,
            cursor: String?,
        ): String = ""

        fun optional(
            @IdempotencyKey key: String?,
        ): String = ""

        fun none(cursor: String?): String = ""
    }

    @Test
    fun `key 쿼리 파라미터를 Idempotency-Key 헤더로 바꾼다`() {
        val parameters = customize("required", listOf(query("key"), query("cursor")))

        assertNull(parameters.firstOrNull { it.`in` == "query" && it.name == "key" })
        val header = parameters.first { it.name == IdempotencyKeyArgumentResolver.HEADER }
        assertEquals("header", header.`in`)
        assertTrue(header.required)
    }

    @Test
    fun `다른 쿼리 파라미터는 그대로 둔다`() {
        val parameters = customize("required", listOf(query("key"), query("cursor")))

        assertEquals(listOf("cursor"), parameters.filter { it.`in` == "query" }.map { it.name })
    }

    @Test
    fun `멱등키가 선택이면 헤더도 선택이다`() {
        val parameters = customize("optional", listOf(query("key")))

        assertFalse(parameters.first { it.name == IdempotencyKeyArgumentResolver.HEADER }.required)
    }

    @Test
    fun `멱등키를 안 받는 핸들러는 건드리지 않는다`() {
        val parameters = customize("none", listOf(query("cursor")))

        assertEquals(listOf("cursor"), parameters.map { it.name })
    }

    @Test
    fun `이미 선언된 헤더를 중복해서 넣지 않는다`() {
        val declared = Parameter().name(IdempotencyKeyArgumentResolver.HEADER).`in`("header").required(true)

        val parameters = customize("required", listOf(query("key"), declared))

        assertEquals(1, parameters.count { it.name == IdempotencyKeyArgumentResolver.HEADER })
    }

    private fun customize(
        methodName: String,
        parameters: List<Parameter>,
    ): List<Parameter> {
        val method = SampleController::class.java.methods.first { it.name == methodName }
        val operation = Operation().parameters(parameters.toMutableList())
        return customizer.customize(operation, HandlerMethod(SampleController(), method)).parameters
    }

    private fun query(name: String) = Parameter().name(name).`in`("query")
}
