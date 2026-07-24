package com.hotelopai.shared.api

import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@RestControllerAdvice
class ApiDeprecationResponseAdvice : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean =
        returnType.getMethodAnnotation(DeprecatedApi::class.java) != null ||
            returnType.containingClass.getAnnotation(DeprecatedApi::class.java) != null

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        val deprecation = returnType.getMethodAnnotation(DeprecatedApi::class.java)
            ?: returnType.containingClass.getAnnotation(DeprecatedApi::class.java)
            ?: return body

        response.headers.set("Deprecation", "true")
        if (deprecation.sunset.isNotBlank()) {
            response.headers.set("Sunset", deprecation.sunset)
        }
        if (deprecation.link.isNotBlank()) {
            response.headers.add("Link", "<${deprecation.link}>; rel=\"deprecation\"")
        }
        if (deprecation.message.isNotBlank()) {
            response.headers.set("X-API-Deprecation-Info", deprecation.message)
        }
        return body
    }
}
