package com.hotelopai.shared.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse

class ApiDeprecationResponseAdviceTest {
    private val advice = ApiDeprecationResponseAdvice()

    @Test
    fun `annotated endpoint responses include deprecation headers`() {
        val response = MockHttpServletResponse()
        val returnType = MethodParameter(
            SampleController::class.java.getDeclaredMethod("deprecatedEndpoint"),
            -1
        )

        val body = advice.beforeBodyWrite(
            body = "ok",
            returnType = returnType,
            selectedContentType = MediaType.TEXT_PLAIN,
            selectedConverterType = StringHttpMessageConverter::class.java,
            request = ServletServerHttpRequest(MockHttpServletRequest("GET", "/api/v1/sample")),
            response = ServletServerHttpResponse(response)
        )

        assertThat(body).isEqualTo("ok")
        assertThat(response.getHeader("Deprecation")).isEqualTo("true")
        assertThat(response.getHeader("Sunset")).isEqualTo("2027-01-01T00:00:00Z")
        assertThat(response.getHeader("Link"))
            .isEqualTo("<https://docs.hotelopai.example/deprecations/sample>; rel=\"deprecation\"")
        assertThat(response.getHeader("X-API-Deprecation-Info")).isEqualTo("Use the replacement endpoint.")
    }

    @Test
    fun `unannotated endpoint responses are unchanged`() {
        val response = MockHttpServletResponse()
        val returnType = MethodParameter(
            SampleController::class.java.getDeclaredMethod("currentEndpoint"),
            -1
        )

        val body = advice.beforeBodyWrite(
            body = "ok",
            returnType = returnType,
            selectedContentType = MediaType.TEXT_PLAIN,
            selectedConverterType = StringHttpMessageConverter::class.java,
            request = ServletServerHttpRequest(MockHttpServletRequest("GET", "/api/v1/sample")),
            response = ServletServerHttpResponse(response)
        )

        assertThat(body).isEqualTo("ok")
        assertThat(response.getHeader("Deprecation")).isNull()
        assertThat(response.getHeader("Sunset")).isNull()
        assertThat(response.getHeader("Link")).isNull()
    }

    private class SampleController {
        @DeprecatedApi(
            sunset = "2027-01-01T00:00:00Z",
            link = "https://docs.hotelopai.example/deprecations/sample",
            message = "Use the replacement endpoint."
        )
        fun deprecatedEndpoint(): String = "ok"

        fun currentEndpoint(): String = "ok"
    }
}
