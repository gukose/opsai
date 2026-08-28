package com.hotelopai.integration.unimock.demo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import jakarta.servlet.FilterChain

class PmsDemoIntegrationAuthenticationFilterTest {
    private val properties=PmsDemoEventProperties(true,"test-shared-key-123","hotel-opai-demo")
    private val filter=PmsDemoIntegrationAuthenticationFilter(properties)

    @AfterEach fun clear()=SecurityContextHolder.clearContext()

    @Test fun `shared key authenticates reset as limited PMS principal`() {
        val request=MockHttpServletRequest("POST","/api/v1/internal/demo/reset/tasks").apply { addHeader("X-Demo-Pms-Key","test-shared-key-123") }
        var called=false
        filter.doFilter(request,MockHttpServletResponse(),FilterChain { _,_->called=true })
        assertThat(called).isTrue()
        val authentication=requireNotNull(SecurityContextHolder.getContext().authentication)
        assertThat(authentication.name).isEqualTo("pms-demo-system")
        assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_PMS_INTEGRATION")
    }

    @Test fun `PMS key does not authenticate unrelated admin APIs`() {
        val request=MockHttpServletRequest("POST","/api/v1/internal/admin/anything").apply { addHeader("X-Demo-Pms-Key","test-shared-key-123") }
        filter.doFilter(request,MockHttpServletResponse(),FilterChain { _,_-> })
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
