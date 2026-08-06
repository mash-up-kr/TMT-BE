package com.tmt.input.http.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
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
    fun errorResponseSchema(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components().also { openApi.components = it }
            ModelConverters
                .getInstance()
                .read(ErrorResponseSchema::class.java)
                .forEach { (name, schema) -> components.addSchemas(name, schema) }
        }

    companion object {
        const val API_NAME = "TMT API"
        const val API_VERSION = "v1"
        const val API_DESCRIPTION = "또맛또(TMT) API"
    }
}
