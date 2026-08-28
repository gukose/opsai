package com.hotelopai.unimock.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import jakarta.annotation.PostConstruct
import java.net.URI

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
        val url=properties.demoConsole.hotelOpaiBaseUrl.trim()
        val parsed=runCatching { URI(url) }.getOrNull()
        if (parsed==null || parsed.scheme !in setOf("http","https") || parsed.host.isNullOrBlank() || parsed.userInfo!=null || parsed.query!=null || parsed.fragment!=null) {
            log.error("PMS demo console disabled: missing or invalid non-secret property unimock.demo-console.hotel-opai-base-url")
            if (url.isBlank()) error("Missing required configuration: unimock.demo-console.hotel-opai-base-url (set OPS_AI_HOTEL_OPAI_BASE_URL)")
            error("Invalid configuration: unimock.demo-console.hotel-opai-base-url must contain an HTTP(S) host")
        }
        log.info("PMS Demo target configured: scheme={} host={} port={}",parsed.scheme,parsed.host,parsed.port)
        if (properties.demoConsole.sharedKey.length<12) {
            log.error("PMS demo console disabled: OPS_AI_PMS_DEMO_SHARED_KEY is missing or too short")
            error("Missing required configuration: unimock.demo-console.shared-key (set OPS_AI_PMS_DEMO_SHARED_KEY)")
        }
    }
}
