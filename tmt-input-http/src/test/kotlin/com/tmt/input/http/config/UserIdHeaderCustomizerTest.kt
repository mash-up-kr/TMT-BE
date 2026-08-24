package com.tmt.input.http.config

import com.tmt.input.http.auth.UserId
import com.tmt.input.http.auth.UserIdArgumentResolver
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import org.junit.jupiter.api.Test
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserIdHeaderCustomizerTest {
    private val customizer = UserIdHeaderCustomizer()

    @Suppress("UNUSED_PARAMETER")
    class SampleController {
        fun authRequired(
            @UserId userId: Long,
            cursor: String?,
        ): String = ""

        fun authOptional(
            @UserId userId: Long?,
        ): String = ""

        fun noAuth(cursor: String?): String = ""
    }

    @Test
    fun `userId 쿼리 파라미터를 X-User-Id 헤더로 바꾼다`() {
        val parameters = customize("authRequired", listOf(query("userId"), query("cursor")))

        assertNull(parameters.firstOrNull { it.`in` == "query" && it.name == "userId" })
        val header = parameters.first { it.name == UserIdArgumentResolver.HEADER }
        assertEquals("header", header.`in`)
        assertTrue(header.required)
    }

    @Test
    fun `다른 쿼리 파라미터는 그대로 둔다`() {
        val parameters = customize("authRequired", listOf(query("userId"), query("cursor")))

        assertEquals(listOf("cursor"), parameters.filter { it.`in` == "query" }.map { it.name })
    }

    @Test
    fun `인증이 선택이면 헤더도 선택이다`() {
        val parameters = customize("authOptional", listOf(query("userId")))

        assertFalse(parameters.first { it.name == UserIdArgumentResolver.HEADER }.required)
    }

    @Test
    fun `인증을 안 쓰는 핸들러는 건드리지 않는다`() {
        val parameters = customize("noAuth", listOf(query("cursor")))

        assertEquals(listOf("cursor"), parameters.map { it.name })
    }

    @Test
    fun `이미 선언된 헤더를 중복해서 넣지 않는다`() {
        val declared = Parameter().name(UserIdArgumentResolver.HEADER).`in`("header").required(true)

        val parameters = customize("authRequired", listOf(query("userId"), declared))

        assertEquals(1, parameters.count { it.name == UserIdArgumentResolver.HEADER })
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
