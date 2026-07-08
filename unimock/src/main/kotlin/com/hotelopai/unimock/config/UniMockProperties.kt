package com.hotelopai.unimock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ops.ai.unimock")
data class UniMockProperties(
    val seed: Seed = Seed()
) {
    data class Seed(
        val path: String = "classpath:/simulation/grand-hotel"
    )
}
