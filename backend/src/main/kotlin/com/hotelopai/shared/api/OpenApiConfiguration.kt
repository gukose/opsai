package com.hotelopai.shared.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.method.HandlerMethod

@Configuration
class OpenApiConfiguration {
    @Bean
    fun hotelOpaiOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Hotel OpAI API")
                    .version(ApiVersions.V1)
                    .description(
                        "Stable v1 REST API. Product endpoints are path-versioned under /api/v1 " +
                            "and use ProblemDetail for error responses."
                    )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_AUTH,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
                    .addSchemas("ProblemDetail", problemDetailSchema())
                    .addSchemas("TaskPageResponse", taskPageSchema())
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    @Bean
    fun publicV1OpenApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("v1")
            .pathsToMatch("/api/v1/**")
            .pathsToExclude("/api/v1/dev/pms/**", "/api/v1/internal/**", "/api/v1/integrations/**", "/api/v1/auth/context")
            .addOperationCustomizer(hotelOpaiOperationCustomizer())
            .addOpenApiCustomizer(hotelOpaiOpenApiCustomizer())
            .build()

    @Bean
    @Profile("local", "test")
    fun developmentPmsOpenApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("dev-pms")
            .pathsToMatch("/api/v1/dev/pms/**")
            .addOperationCustomizer(hotelOpaiOperationCustomizer())
            .addOpenApiCustomizer(hotelOpaiOpenApiCustomizer())
            .build()

    @Bean
    fun hotelOpaiOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
            operation.operationId = stableOperationId(handlerMethod)
            operation.responses = operation.responses ?: ApiResponses()
            operation.responses.forEach { (_, response) -> addApiVersionHeader(response) }
            if (handlerMethod.getMethodAnnotation(DeprecatedApi::class.java) != null ||
                handlerMethod.beanType.getAnnotation(DeprecatedApi::class.java) != null
            ) {
                operation.deprecated = true
                operation.responses.forEach { (_, response) -> addDeprecationHeaders(response) }
            }
            operation
        }

    @Bean
    fun hotelOpaiOpenApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.components = openApi.components ?: Components()
            openApi.components
                .addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
                .addSchemas("ProblemDetail", problemDetailSchema())
                .addSchemas("TaskPageResponse", taskPageSchema())

            openApi.paths.orEmpty().forEach { (path, pathItem) ->
                pathItem.readOperations().forEach { operation ->
                    operation.responses = operation.responses ?: ApiResponses()
                    ensureStandardProblemResponses(operation.responses)
                    operation.responses.forEach { (_, response) -> addApiVersionHeader(response) }
                    if (path == "/api/v1/auth/login" || path == "/api/v1/auth/refresh") {
                        operation.security = emptyList()
                    } else {
                        operation.security = listOf(SecurityRequirement().addList(BEARER_AUTH))
                    }
                    if (path == "/api/v1/tasks" && operation.operationId == "TaskController_listTasks") {
                        documentTaskListCompatibilityResponse(operation)
                    }
                }
            }
        }

    private fun stableOperationId(handlerMethod: HandlerMethod): String =
        "${handlerMethod.beanType.simpleName}_${handlerMethod.method.name}"

    private fun ensureStandardProblemResponses(responses: ApiResponses) {
        STANDARD_ERROR_STATUSES.forEach { status ->
            responses.computeIfAbsent(status) {
                ApiResponse()
                    .description(problemDescription(status))
                    .content(
                        io.swagger.v3.oas.models.media.Content().addMediaType(
                            "application/problem+json",
                            io.swagger.v3.oas.models.media.MediaType().schema(
                                Schema<Any>().`$ref`("#/components/schemas/ProblemDetail")
                            )
                        )
                    )
            }
        }
    }

    private fun problemDescription(status: String): String =
        when (status) {
            "400" -> "Invalid request"
            "401" -> "Authentication required"
            "403" -> "Permission denied"
            "404" -> "Resource not found"
            "409" -> "Conflict"
            "429" -> "Rate limit exceeded"
            else -> "Unexpected server error"
        }

    private fun addApiVersionHeader(response: ApiResponse) {
        response.headers = response.headers ?: linkedMapOf()
        response.headers.computeIfAbsent(ApiVersions.VERSION_HEADER) {
            Header()
                .description("API version that handled the request.")
                .schema(StringSchema()._default(ApiVersions.V1))
        }
    }

    private fun addDeprecationHeaders(response: ApiResponse) {
        response.headers = response.headers ?: linkedMapOf()
        response.headers.computeIfAbsent("Deprecation") {
            Header().description("Set to true when the endpoint is deprecated.").schema(StringSchema())
        }
        response.headers.computeIfAbsent("Sunset") {
            Header().description("Optional timestamp after which the endpoint may be removed.").schema(StringSchema())
        }
        response.headers.computeIfAbsent("Link") {
            Header().description("Optional deprecation documentation link.").schema(StringSchema())
        }
        response.headers.computeIfAbsent("X-API-Deprecation-Info") {
            Header().description("Optional short migration note.").schema(StringSchema())
        }
    }

    private fun documentTaskListCompatibilityResponse(operation: Operation) {
        val response = operation.responses?.get("200") ?: return
        response.content = Content().addMediaType(
            "*/*",
            MediaType().schema(
                ComposedSchema()
                    .oneOf(
                        listOf(
                            ArraySchema().items(Schema<Any>().`$ref`("#/components/schemas/TaskResponse")),
                            Schema<Any>().`$ref`("#/components/schemas/TaskPageResponse")
                        )
                    )
            )
        )
    }

    private fun problemDetailSchema(): Schema<Any> =
        ObjectSchema()
            .addProperty("type", StringSchema().format("uri"))
            .addProperty("title", StringSchema())
            .addProperty("status", IntegerSchema().format("int32"))
            .addProperty("detail", StringSchema())
            .addProperty("instance", StringSchema().format("uri"))
            .required(listOf("title", "status", "detail")) as Schema<Any>

    private fun taskPageSchema(): Schema<Any> =
        ObjectSchema()
            .addProperty("items", ArraySchema().items(Schema<Any>().`$ref`("#/components/schemas/TaskResponse")))
            .addProperty("page", IntegerSchema().format("int32"))
            .addProperty("size", IntegerSchema().format("int32"))
            .addProperty("totalItems", IntegerSchema().format("int64"))
            .addProperty("totalPages", IntegerSchema().format("int32"))
            .addProperty("hasNext", Schema<Boolean>().type("boolean"))
            .addProperty("hasPrevious", Schema<Boolean>().type("boolean"))
            .required(listOf("items", "page", "size", "totalItems", "totalPages", "hasNext", "hasPrevious")) as Schema<Any>

    companion object {
        private const val BEARER_AUTH = "bearerAuth"
        private val STANDARD_ERROR_STATUSES = listOf("400", "401", "403", "404", "409", "429", "500")
    }
}
