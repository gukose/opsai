package com.hotelopai.unimock.application.pms

open class PmsReadException(message: String) : RuntimeException(message)

class PmsResourceNotFoundException(resourceType: String, resourceId: String) :
    PmsReadException("$resourceType not found: $resourceId")
