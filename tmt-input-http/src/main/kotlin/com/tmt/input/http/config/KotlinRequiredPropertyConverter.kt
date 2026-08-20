package com.tmt.input.http.config

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.providers.ObjectMapperProvider
import kotlin.reflect.full.primaryConstructor

/**
 * Kotlin non-null 프로퍼티를 OpenAPI `required`에 싣는다.
 *
 * springdoc의 `KotlinNullablePropertyCustomizer`는 nullable만 채우고 required는 채우지 않는다.
 * 그대로 두면 생성된 클라이언트 타입이 전부 optional이 된다.
 *
 * 기본값이 있는 파라미터(`isOptional`)는 제외한다 — 클라이언트가 생략하면 서버가 채우므로
 * `required`가 아니라 "키 생략 가능"이 맞다.
 */
class KotlinRequiredPropertyConverter(
    private val objectMapperProvider: ObjectMapperProvider,
) : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>,
    ): Schema<*>? {
        val resolved = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
        val target = resolved?.let { definitionOf(it, context) } ?: return resolved
        val properties = target.properties ?: return resolved

        requiredNames(type)
            .filter { properties.containsKey(it) }
            .filterNot { target.required?.contains(it) == true }
            .forEach { target.addRequiredItem(it) }

        return resolved
    }

    /** 객체 타입은 `$ref`로 돌아오므로 실제 정의를 찾아 거기에 채운다. */
    private fun definitionOf(
        schema: Schema<*>,
        context: ModelConverterContext,
    ): Schema<*> {
        val ref = schema.`$ref` ?: return schema
        return context.definedModels[ref.substringAfterLast('/')] ?: schema
    }

    private fun requiredNames(type: AnnotatedType): List<String> {
        val kClass =
            runCatching {
                objectMapperProvider
                    .jsonMapper()
                    .constructType(type.type)
                    .rawClass
                    .kotlin
            }.getOrNull() ?: return emptyList()

        val constructor = runCatching { kClass.primaryConstructor }.getOrNull() ?: return emptyList()

        return constructor.parameters
            .filterNot { it.isOptional }
            .filterNot { it.type.isMarkedNullable }
            .mapNotNull { it.name }
    }
}
