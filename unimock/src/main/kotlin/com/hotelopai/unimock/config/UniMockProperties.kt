package com.hotelopai.unimock.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import jakarta.annotation.PostConstruct

@ConfigurationProperties(prefix = "ops.ai.unimock")
data class UniMockProperties(
    val seed: Seed = Seed(),
    val demoConsole: DemoConsole = DemoConsole()
) {
    data class Seed(
        val path: String = "classpath:/simulation/grand-hotel"
    )

    data class DemoConsole(
        val hotelOpaiBaseUrl: String = "",
        val hotelCode: String = "hotel-opai-demo",
        val sharedKey: String = ""
    )
}

@Component
@Profile("prod")
class DemoConsoleConfigurationCheck(private val properties: UniMockProperties) {
    private val log=LoggerFactory.getLogger(javaClass)
    @PostConstruct
    fun validate() {
        val url=properties.demoConsole.hotelOpaiBaseUrl
        if (url.isBlank() || !url.startsWith("http://") && !url.startsWith("https://")) {
            log.error("PMS demo console disabled: OPS_AI_HOTEL_OPAI_BASE_URL must be an internal Hotel OpAI HTTP(S) URL")
            error("PMS demo console delivery is not configured")
        }
        if (properties.demoConsole.sharedKey.length<12) {
            log.error("PMS demo console disabled: OPS_AI_PMS_DEMO_SHARED_KEY is missing or too short")
            error("PMS demo console delivery is not configured")
        }
    }
}
