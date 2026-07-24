package com.hotelopai.shared.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiVersionHeaderFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI == ApiVersions.V1_PATH_PREFIX ||
            request.requestURI.startsWith("${ApiVersions.V1_PATH_PREFIX}/")
        ) {
            response.setHeader(ApiVersions.VERSION_HEADER, ApiVersions.V1)
        }
        filterChain.doFilter(request, response)
    }
}
