package com.hotelopai.demo

import com.hotelopai.config.DatabaseProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

class DemoDeploymentConfigurationTest {
    @Test
    fun `demo datasource binds every field from Supabase component environment`() {
        val properties = bindDemoDatabase(mapOf(
            "SPRING_PROFILES_ACTIVE" to "demo",
            "SUPABASE_DB_HOST" to "aws-0-eu.pooler.supabase.com",
            "SUPABASE_DB_PORT" to "5432",
            "SUPABASE_DB_NAME" to "postgres",
            "SUPABASE_DB_USER" to "postgres.project",
            "SUPABASE_DB_PASSWORD" to "not-a-real-secret",
            "OPS_AI_DB_MAXIMUM_POOL_SIZE" to "5",
            "OPS_AI_DB_MINIMUM_IDLE" to "1",
            "OPS_AI_DB_CONNECTION_TIMEOUT_MS" to "20000"
        ))

        assertThat(properties.url).isEqualTo("jdbc:postgresql://aws-0-eu.pooler.supabase.com:5432/postgres?sslmode=require")
        assertThat(properties.username).isEqualTo("postgres.project")
        assertThat(properties.password).isEqualTo("not-a-real-secret")
        assertThat(properties.driverClassName).isEqualTo("org.postgresql.Driver")
        assertThat(properties.maximumPoolSize).isEqualTo(5)
        assertThat(properties.minimumIdle).isEqualTo(1)
        assertThat(properties.connectionTimeout).isEqualTo(20_000)
    }

    @Test
    fun `demo datasource binds every field from recommended Railway JDBC environment`() {
        val properties = bindDemoDatabase(mapOf(
            "SPRING_PROFILES_ACTIVE" to "demo",
            "OPS_AI_DB_URL" to "jdbc:postgresql://pooler.example.com:5432/postgres?sslmode=require",
            "OPS_AI_DB_USERNAME" to "postgres.project",
            "OPS_AI_DB_PASSWORD" to "not-a-real-secret"
        ))

        assertThat(properties.url).isEqualTo("jdbc:postgresql://pooler.example.com:5432/postgres?sslmode=require")
        assertThat(properties.username).isEqualTo("postgres.project")
        assertThat(properties.password).isEqualTo("not-a-real-secret")
        assertThat(properties.driverClassName).isEqualTo("org.postgresql.Driver")
        assertThat(properties.maximumPoolSize).isEqualTo(5)
        assertThat(properties.minimumIdle).isEqualTo(1)
        assertThat(properties.connectionTimeout).isEqualTo(20_000)
    }

    @Test
    fun `database numeric pool settings retain safe constructor defaults when absent`() {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("required-database-values", mapOf(
            "ops.ai.database.url" to "jdbc:postgresql://pooler.example.com:5432/postgres?sslmode=require",
            "ops.ai.database.username" to "postgres.project",
            "ops.ai.database.password" to "not-a-real-secret"
        )))

        val properties = Binder.get(environment).bind("ops.ai.database", DatabaseProperties::class.java).get()

        assertThat(properties.driverClassName).isEqualTo("org.postgresql.Driver")
        assertThat(properties.maximumPoolSize).isEqualTo(5)
        assertThat(properties.minimumIdle).isEqualTo(1)
        assertThat(properties.connectionTimeout).isEqualTo(20_000)
    }

    @Test
    fun `demo passwords are required and never have source defaults`() {
        val missing = DemoBootstrapProperties(enabled = true)
        assertThatThrownBy { missing.passwordFor("GM") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(DemoBootstrapProperties(gmPassword = "review-only-password").passwordFor("GM"))
            .isEqualTo("review-only-password")
    }

    @Test
    fun `demo configuration has no local network dependency and preserves production separation`() {
        val demo = ClassPathResource("application-demo.yml").inputStream.bufferedReader().readText()
        val production = ClassPathResource("application-prod.yml").inputStream.bufferedReader().readText()
        assertThat(demo).doesNotContain("localhost", "127.0.0.1", "active-provider: apaleo")
        assertThat(demo).contains("active-provider: internal-demo", "forward-headers-strategy: framework")
        assertThat(production).contains("seed:\n        enabled: false")
    }

    private fun bindDemoDatabase(environmentValues: Map<String, Any>): DatabaseProperties {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("demo-test", environmentValues))
        YamlPropertySourceLoader().load("application-demo", ClassPathResource("application-demo.yml"))
            .reversed().forEach(environment.propertySources::addLast)
        return Binder.get(environment).bind("ops.ai.database", DatabaseProperties::class.java).get()
    }
}
