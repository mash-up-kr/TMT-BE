package com.tmt.input.http.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.Schema
import org.junit.jupiter.api.Test
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.providers.ObjectMapperProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlinPropertyConverterTest {
    private val converters =
        ModelConverters().apply {
            addConverter(KotlinPropertyConverter(ObjectMapperProvider(SpringDocConfigProperties())))
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

    data class Flags(
        val placeId: String,
        val isFavorite: Boolean,
        val isMine: Boolean?,
    )

    data class Renamed(
        @param:JsonProperty("favorite") val isFavorite: Boolean,
    )

    data class NotAPrefix(
        val island: String,
        val isle: String,
    )

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

    @Test
    fun `is 접두 boolean은 직렬화 이름 그대로 나간다`() {
        // swagger는 Java bean 규약으로 읽어 favorite으로 잡지만 앱은 isFavorite을 내보낸다
        val flags = schemasOf(Flags::class.java).getValue("Flags")

        assertEquals(setOf("placeId", "isFavorite", "isMine"), flags.properties.keys)
    }

    @Test
    fun `이름을 되돌려도 필드 순서는 그대로다`() {
        val untouched = ModelConverters().readAll(Flags::class.java).getValue("Flags")
        val converted = schemasOf(Flags::class.java).getValue("Flags")

        assertEquals(
            untouched.properties.keys.indexOf("favorite"),
            converted.properties.keys.indexOf("isFavorite"),
        )
    }

    @Test
    fun `이름을 되돌린 is 접두 boolean도 required에 실린다`() {
        val flags = schemasOf(Flags::class.java).getValue("Flags")

        assertEquals(listOf("isFavorite", "placeId"), flags.required.sorted())
    }

    @Test
    fun `JsonProperty로 지정한 이름은 건드리지 않는다`() {
        // 지정한 이름이 실제 직렬화 이름이다
        val renamed = schemasOf(Renamed::class.java).getValue("Renamed")

        assertEquals(listOf("favorite"), renamed.properties.keys.toList())
    }

    @Test
    fun `is 뒤가 소문자면 접두사가 아니라 이름의 일부다`() {
        val schema = schemasOf(NotAPrefix::class.java).getValue("NotAPrefix")

        assertEquals(listOf("island", "isle"), schema.properties.keys.toList())
    }

    @Test
    fun `같은 타입을 여러 번 읽어도 이름이 어긋나지 않는다`() {
        schemasOf(Flags::class.java)
        val flags = schemasOf(Flags::class.java).getValue("Flags")

        assertEquals(setOf("placeId", "isFavorite", "isMine"), flags.properties.keys)
    }
}
