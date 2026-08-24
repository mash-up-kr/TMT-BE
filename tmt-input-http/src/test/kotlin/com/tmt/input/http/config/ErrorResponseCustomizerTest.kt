package com.tmt.input.http.config

import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.junit.jupiter.api.Test
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorResponseCustomizerTest {
    private val customizer = ErrorResponseCustomizer()

    @Suppress("UNUSED_PARAMETER")
    class SampleController {
        fun noInput(): String = ""

        fun authRequired(
            @UserId userId: Long,
        ): String = ""

        fun authOptional(
            @UserId userId: Long?,
        ): String = ""

        @ApiErrorCodes(
            ErrorCode.REVIEW_TAG_NOT_FOUND,
            ErrorCode.PLACE_NOT_FOUND,
            ErrorCode.GROUP_NAME_DUPLICATED,
        )
        fun declared(
            @UserId userId: Long,
        ): String = ""
    }

    private fun customize(
        methodName: String,
        parameters: List<Parameter> = emptyList(),
        responses: ApiResponses = ApiResponses(),
    ): ApiResponses {
        val method = SampleController::class.java.methods.first { it.name == methodName }
        val operation = Operation().responses(responses)
        if (parameters.isNotEmpty()) operation.parameters(parameters)
        return customizer.customize(operation, HandlerMethod(SampleController(), method)).responses
    }

    private fun query(name: String) = Parameter().name(name).`in`("query")

    private fun header(name: String) = Parameter().name(name).`in`("header")

    private fun descriptionOf(
        responses: ApiResponses,
        status: String,
    ) = responses.getValue(status).description

    @Test
    fun `모든 오퍼레이션에 500을 단다`() {
        val responses = customize("noInput")

        assertTrue(descriptionOf(responses, "500").contains("`INTERNAL_ERROR`"))
    }

    @Test
    fun `요청에서 값을 읽지 않는 핸들러에는 400을 달지 않는다`() {
        val responses = customize("noInput")

        assertEquals(listOf("500"), responses.keys.toList())
    }

    @Test
    fun `요청에서 값을 읽는 핸들러에 400을 단다`() {
        val responses = customize("authOptional")

        assertTrue(descriptionOf(responses, "400").contains("`VALIDATION_FAILED`"))
    }

    @Test
    fun `UserId가 필수면 401을 단다`() {
        val responses = customize("authRequired")

        assertTrue(descriptionOf(responses, "401").contains("`UNAUTHORIZED`"))
    }

    @Test
    fun `UserId가 nullable이면 401을 달지 않는다`() {
        val responses = customize("authOptional")

        assertFalse(responses.containsKey("401"))
    }

    @Test
    fun `cursor를 받으면 400에 INVALID_CURSOR를 싣는다`() {
        val responses = customize("authRequired", parameters = listOf(query("cursor")))

        assertTrue(descriptionOf(responses, "400").contains("`INVALID_CURSOR`"))
    }

    @Test
    fun `Idempotency-Key를 받으면 409를 단다`() {
        val responses = customize("authRequired", parameters = listOf(header("Idempotency-Key")))

        assertTrue(descriptionOf(responses, "409").contains("`IDEMPOTENCY_CONFLICT`"))
    }

    @Test
    fun `선언한 코드를 ErrorType이 정한 상태에 싣는다`() {
        val responses = customize("declared")

        assertTrue(descriptionOf(responses, "404").contains("`PLACE_NOT_FOUND`"))
        assertTrue(descriptionOf(responses, "409").contains("`GROUP_NAME_DUPLICATED`"))
    }

    @Test
    fun `같은 상태로 묶이는 코드는 한 응답에 모은다`() {
        val description = descriptionOf(customize("declared"), "400")

        assertTrue(description.contains("`VALIDATION_FAILED`"))
        assertTrue(description.contains("`REVIEW_TAG_NOT_FOUND`"))
    }

    @Test
    fun `실패 응답 바디는 ErrorResponse를 참조한다`() {
        val content = customize("noInput").getValue("500").content

        assertEquals(
            ErrorResponseCustomizer.ERROR_RESPONSE_REF,
            content
                .getValue(ErrorResponseCustomizer.PROBLEM_JSON)
                .schema.`$ref`,
        )
    }

    @Test
    fun `이미 선언된 상태 코드는 덮지 않는다`() {
        val declared = ApiResponses().addApiResponse("500", ApiResponse().description("직접 선언"))

        val responses = customize("noInput", responses = declared)

        assertEquals("직접 선언", descriptionOf(responses, "500"))
    }

    @Test
    fun `성공 응답은 그대로 둔다`() {
        val declared = ApiResponses().addApiResponse("201", ApiResponse().description("생성"))

        val responses = customize("authRequired", responses = declared)

        assertEquals("생성", descriptionOf(responses, "201"))
        assertEquals(listOf("201", "400", "401", "500"), responses.keys.toList())
    }
}
