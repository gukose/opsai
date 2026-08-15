package com.hotelopai.knowledge.application

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "demo", "prod")
@EnableConfigurationProperties(KnowledgeProperties::class)
class KnowledgeEmbeddingRefreshScheduler(
    private val embeddingService: KnowledgeEmbeddingService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: KnowledgeProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.knowledge.semantic-search.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.knowledge.semantic-search.schedule.execution-interval:PT10M}"
    )
    fun run() {
        if (!properties.semanticSearch.schedule.enabled) return
        scheduledJobRunner.runSingleton(KnowledgeEmbeddingService.SCHEDULE_JOB_NAME, properties.semanticSearch.schedule.lockTimeout) {
            embeddingService.processScheduledBatch()
        }
    }
}
