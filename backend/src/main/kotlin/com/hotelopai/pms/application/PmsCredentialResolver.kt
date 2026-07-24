package com.hotelopai.pms.application

import com.hotelopai.pms.domain.PmsProviderConfigurationFailureException
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

interface PmsCredentialResolver {
    fun resolve(reference: String): String
}

@Component
class EnvironmentPmsCredentialResolver(
    private val environment: Environment
) : PmsCredentialResolver {
    override fun resolve(reference: String): String {
        val key = when {
            reference.startsWith("env:") -> reference.removePrefix("env:")
            reference.startsWith("\${") && reference.endsWith("}") -> reference.removePrefix("\${").removeSuffix("}")
            else -> throw PmsProviderConfigurationFailureException(
                "Unsupported PMS credential reference '$reference'. Use env:VARIABLE_NAME."
            )
        }

        val value = environment.getProperty(key).orEmpty()
        if (value.isBlank()) {
            throw PmsProviderConfigurationFailureException(
                "PMS credential reference '$reference' could not be resolved."
            )
        }
        return value
    }
}
