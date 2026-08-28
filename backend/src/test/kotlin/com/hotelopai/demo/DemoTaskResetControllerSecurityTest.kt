package com.hotelopai.demo

import com.hotelopai.shared.security.CurrentUserContext
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionGuard
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

class DemoTaskResetControllerSecurityTest {
    @AfterEach
    fun clearSecurity() = SecurityContextHolder.clearContext()

    @Test
    fun `admin role can invoke reset while non-admin is forbidden`() {
        val context = testContext("test")
        val controller = context.getBean(DemoTaskResetController::class.java)
        val guard = context.getBean(PermissionGuard::class.java)
        val service = context.getBean(DemoTaskResetService::class.java)
        val hotelId = UUID.randomUUID()
        `when`(context.getBean(CurrentUserContextResolver::class.java).current()).thenReturn(current(hotelId))
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "n/a")

        `when`(guard.hasRole("ADMIN")).thenReturn(false)
        assertThatThrownBy(controller::resetTasks).isInstanceOf(AccessDeniedException::class.java)

        `when`(guard.hasRole("ADMIN")).thenReturn(true)
        `when`(service.resetTasks(hotelId)).thenReturn(DemoTaskResetResult(2, 4, 0))
        assertThat(controller.resetTasks()).isEqualTo(DemoTaskResetResult(2, 4, 0))
        verify(service).resetTasks(hotelId)
        context.close()
    }

    @Test
    fun `PMS integration principal can invoke only the configured demo reset path`() {
        val context = testContext("test")
        val controller = context.getBean(DemoTaskResetController::class.java)
        val guard = context.getBean(PermissionGuard::class.java)
        val service = context.getBean(DemoTaskResetService::class.java)
        `when`(guard.hasRole("ADMIN")).thenReturn(false)
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(
            "pms-demo-system", "n/a", listOf(SimpleGrantedAuthority("ROLE_PMS_INTEGRATION"))
        )
        `when`(service.configuredDemoStatus()).thenReturn(DemoTaskResetStatus("hotel-opai-demo", 14))
        `when`(service.resetConfiguredDemoTasks()).thenReturn(DemoTaskResetResult(14, 30, 0))

        assertThat(controller.status()).isEqualTo(DemoTaskResetStatus("hotel-opai-demo", 14))
        assertThat(controller.resetTasks()).isEqualTo(DemoTaskResetResult(14, 30, 0))
        verify(service).configuredDemoStatus()
        verify(service).resetConfiguredDemoTasks()
        verify(context.getBean(CurrentUserContextResolver::class.java), never()).current()
        context.close()
    }

    @Test
    fun `controller is not registered in production profile`() {
        val context = AnnotationConfigApplicationContext().also {
            it.environment.setActiveProfiles("prod")
            it.register(DemoTaskResetController::class.java, DemoTaskResetService::class.java)
            it.refresh()
        }
        assertThat(context.getBeansOfType(DemoTaskResetController::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DemoTaskResetService::class.java)).isEmpty()
        context.close()
    }

    @Test
    fun `production profile wins even if demo profile is also active`() {
        val context = AnnotationConfigApplicationContext().also {
            it.environment.setActiveProfiles("prod", "demo")
            it.register(DemoTaskResetController::class.java, DemoTaskResetService::class.java)
            it.refresh()
        }
        assertThat(context.getBeansOfType(DemoTaskResetController::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DemoTaskResetService::class.java)).isEmpty()
        context.close()
    }

    private fun testContext(profile: String): AnnotationConfigApplicationContext =
        AnnotationConfigApplicationContext().also { context ->
            context.environment.setActiveProfiles(profile)
            context.register(TestConfiguration::class.java, DemoTaskResetController::class.java)
            context.refresh()
        }

    private fun current(hotelId: UUID) = CurrentUserContext(
        userId = UUID.randomUUID(),
        hotelId = hotelId,
        sessionId = UUID.randomUUID(),
        permissions = emptySet(),
        roles = setOf("ADMIN")
    )

    @Configuration
    @EnableMethodSecurity
    class TestConfiguration {
        @Bean fun demoTaskResetService(): DemoTaskResetService = mock(DemoTaskResetService::class.java)
        @Bean fun currentUserContextResolver(): CurrentUserContextResolver = mock(CurrentUserContextResolver::class.java)
        @Bean fun permissionGuard(): PermissionGuard = mock(PermissionGuard::class.java)
    }
}
