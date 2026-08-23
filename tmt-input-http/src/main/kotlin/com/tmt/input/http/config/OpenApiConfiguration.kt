package com.tmt.input.http.config

import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.providers.ObjectMapperProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI {
        val info =
            Info()
                .title(API_NAME)
                .version(API_VERSION)
                .description(API_DESCRIPTION)

        val local =
            Server()
                .url("http://localhost:8080/api")
                .description("Local")

        return OpenAPI()
            .components(Components())
            .info(info)
            .servers(listOf(local))
    }

    @Bean
    fun kotlinPropertyConverter(objectMapperProvider: ObjectMapperProvider): ModelConverter =
        KotlinPropertyConverter(objectMapperProvider)

    @Bean
    fun errorResponses(): OperationCustomizer = ErrorResponseCustomizer()

    @Bean
    fun errorResponseSchema(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components().also { openApi.components = it }
            // 문서가 OpenAPI 3.1이므로 3.1 인스턴스로 읽는다. 3.0 싱글턴은 등록된 ModelConverter를 공유하지 않는다
            ModelConverters
                .getInstance(true)
                .read(ErrorResponseSchema::class.java)
                .forEach { (name, schema) -> components.addSchemas(name, schema) }
        }

    companion object {
        const val API_NAME = "TMT API"
        const val API_VERSION = "v1"
        const val API_DESCRIPTION = "또맛또(TMT) API"
    }
}
