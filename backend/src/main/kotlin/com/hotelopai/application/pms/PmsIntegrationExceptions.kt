package com.hotelopai.integration.unimock

open class PmsIntegrationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class PmsIntegrationUnavailableException(
    message: String,
    cause: Throwable? = null
) : PmsIntegrationException(message, cause)

class PmsIntegrationTimeoutException(
    message: String,
    cause: Throwable? = null
) : PmsIntegrationException(message, cause)

class PmsResourceNotFoundException(
    message: String
) : RuntimeException(message) {
    constructor(resourceType: String, resourceId: String) : this("$resourceType not found: $resourceId")
}
