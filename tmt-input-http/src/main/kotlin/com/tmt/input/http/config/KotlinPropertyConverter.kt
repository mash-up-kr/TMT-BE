package com.tmt.input.http.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.providers.ObjectMapperProvider
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private val logger = KotlinLogging.logger {}

/**
 * 스펙을 읽는 swagger-core는 Jackson Kotlin 모듈 없이 introspection하므로, 앱이 실제로 내보내는
 * JSON과 두 군데가 어긋난다. 주 생성자를 직접 읽어 그 둘을 맞춘다.
 *
 * 1. **프로퍼티 이름** — `val isFavorite: Boolean`을 Java bean 규약으로 읽어 `favorite`으로 내보낸다.
 *    앱은 Kotlin 모듈로 `isFavorite`을 직렬화하므로 스펙과 응답이 달라진다.
 * 2. **`required`** — springdoc의 `KotlinNullablePropertyCustomizer`는 nullable만 채우고
 *    non-null 프로퍼티를 `required`에 싣지 않는다. 그대로 두면 생성된 클라이언트 타입이 전부 optional이 된다.
 *
 * 기본값이 있는 파라미터(`isOptional`)는 `required`에서 제외한다 — 클라이언트가 생략하면 서버가 채우므로
 * `required`가 아니라 "키 생략 가능"이 맞다.
 */
class KotlinPropertyConverter(
    private val objectMapperProvider: ObjectMapperProvider,
) : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>,
    ): Schema<*>? {
        val resolved = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
        val target = resolved?.let { definitionOf(it, context) } ?: return resolved
        if (target.properties.isNullOrEmpty()) return resolved

        val parameters = primaryConstructorParameters(type)

        parameters.forEach { restoreIsPrefix(it, target) }

        parameters
            .filterNot { it.isOptional }
            .filterNot { it.type.isMarkedNullable }
            .mapNotNull { it.name }
            .filter { target.properties.containsKey(it) }
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

    /**
     * swagger가 `isFavorite`을 `favorite`으로 잡은 것을 되돌린다.
     *
     * `@JsonProperty`로 이름을 지정했으면 그쪽이 실제 직렬화 이름이므로 건드리지 않는다.
     * `island`처럼 `is` 뒤가 소문자면 접두사가 아니라 이름의 일부라 대상이 아니다.
     */
    private fun restoreIsPrefix(
        parameter: KParameter,
        target: Schema<*>,
    ) {
        val name = parameter.name ?: return
        if (!IS_PREFIX.containsMatchIn(name)) return
        if (parameter.type.classifier != Boolean::class) return
        if (!parameter.findAnnotation<JsonProperty>()?.value.isNullOrEmpty()) return

        val properties = target.properties
        if (properties.containsKey(name)) return

        val beanName = name.removePrefix("is").replaceFirstChar { it.lowercaseChar() }
        if (!properties.containsKey(beanName)) return

        // LinkedHashMap이라 remove·put하면 필드 순서가 바뀐다. 자리를 지키려고 다시 만든다
        val renamed = properties.mapKeys { (key, _) -> if (key == beanName) name else key }
        properties.clear()
        properties.putAll(renamed)

        target.required?.replaceAll { if (it == beanName) name else it }
    }

    private fun primaryConstructorParameters(type: AnnotatedType): List<KParameter> {
        val kClass =
            runCatching {
                objectMapperProvider
                    .jsonMapper()
                    .constructType(type.type)
                    .rawClass
                    .kotlin
            }.onFailure { e ->
                logger.warn(e) { "Kotlin 타입 해석 실패 - 스펙을 보정하지 않는다: ${type.type}" }
            }.getOrNull() ?: return emptyList()

        val constructor =
            runCatching { kClass.primaryConstructor }
                .onFailure { e ->
                    logger.warn(e) { "주 생성자 조회 실패 - 스펙을 보정하지 않는다: ${kClass.qualifiedName}" }
                }.getOrNull() ?: return emptyList()

        return constructor.parameters
    }

    companion object {
        private val IS_PREFIX = Regex("^is[A-Z]")
    }
}
