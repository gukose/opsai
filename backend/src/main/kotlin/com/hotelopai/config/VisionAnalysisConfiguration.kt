package com.hotelopai.config

import com.hotelopai.vision.application.VisionAnalysisPort
import com.hotelopai.vision.application.VisionAnalysisProperties
import com.hotelopai.vision.infrastructure.deterministic.DeterministicVisionAnalysisProvider
import com.hotelopai.vision.infrastructure.unavailable.UnavailableVisionAnalysisProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

@Configuration
@EnableConfigurationProperties(VisionAnalysisProperties::class)
class VisionAnalysisConfiguration {
    @Bean
    fun deterministicVisionAnalysisProvider(): DeterministicVisionAnalysisProvider =
        DeterministicVisionAnalysisProvider()

    @Bean
    fun visionAnalysisPort(
        properties: VisionAnalysisProperties,
        deterministicProvider: DeterministicVisionAnalysisProvider,
        environment: Environment
    ): VisionAnalysisPort =
        if (
            properties.normalizedProvider() == VisionAnalysisProperties.Provider.DETERMINISTIC ||
            properties.deterministicFixturesEnabled ||
            environment.acceptsProfiles(Profiles.of("test"))
        ) {
            deterministicProvider
        } else {
            UnavailableVisionAnalysisProvider
        }
}
