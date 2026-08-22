package com.tmt.input.http.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.Schema
import org.junit.jupiter.api.Test
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.providers.ObjectMapperProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlinRequiredPropertyConverterTest {
    private val converters =
        ModelConverters().apply {
            addConverter(KotlinRequiredPropertyConverter(ObjectMapperProvider(SpringDocConfigProperties())))
        }

    private fun schemasOf(type: Class<*>): Map<String, Schema<*>> =
        converters.readAll(type).mapValues { (_, schema) -> schema as Schema<*> }

    data class Sample(
        val id: String,
        val name: String,
        val imageUrl: String?,
        val limit: Int = 20,
    )

    data class Outer(
        val inner: Inner,
        val optionalInner: Inner?,
    )

    data class Inner(
        val value: String,
    )

    class JavaStyle {
        var anything: String? = null
    }

    @Test
    fun `non-null 프로퍼티를 required에 싣는다`() {
        val sample = schemasOf(Sample::class.java).getValue("Sample")

        assertEquals(listOf("id", "name"), sample.required)
    }

    @Test
    fun `nullable 프로퍼티는 required에서 빠진다`() {
        val sample = schemasOf(Sample::class.java).getValue("Sample")

        assertEquals(false, sample.required.contains("imageUrl"))
    }

    @Test
    fun `기본값이 있는 파라미터는 required에서 빠진다`() {
        // 클라이언트가 생략하면 서버가 채우므로 "키 생략 가능"이 맞다
        val sample = schemasOf(Sample::class.java).getValue("Sample")

        assertEquals(false, sample.required.contains("limit"))
    }

    @Test
    fun `참조로 실리는 중첩 객체의 정의에도 required를 채운다`() {
        val schemas = schemasOf(Outer::class.java)

        assertEquals(listOf("inner"), schemas.getValue("Outer").required)
        assertEquals(listOf("value"), schemas.getValue("Inner").required)
    }

    @Test
    fun `주 생성자가 없는 클래스는 건드리지 않는다`() {
        val schema = schemasOf(JavaStyle::class.java).getValue("JavaStyle")

        assertNull(schema.required)
    }

    @Test
    fun `같은 타입을 여러 번 읽어도 required가 중복되지 않는다`() {
        schemasOf(Outer::class.java)
        val schemas = schemasOf(Outer::class.java)

        assertEquals(listOf("value"), schemas.getValue("Inner").required)
    }
}
