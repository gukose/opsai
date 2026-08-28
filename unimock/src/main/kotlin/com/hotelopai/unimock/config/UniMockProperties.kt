package com.hotelopai.unimock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ops.ai.unimock")
data class UniMockProperties(
    val seed: Seed = Seed(),
    val demoConsole: DemoConsole = DemoConsole()
) {
    data class Seed(
        val path: String = "classpath:/simulation/grand-hotel"
    )

    data class DemoConsole(
        val hotelOpaiBaseUrl: String = "http://localhost:8080",
        val hotelCode: String = "hotel-opai-demo",
        val sharedKey: String = ""
    )
}
