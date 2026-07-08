package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

interface CompletionPolicy {
    fun evaluate(task: Task, now: Instant = Instant.now()): CompletionDecision
}

data class CompletionDecision(
    val requiresPmsUpdate: Boolean,
    val verificationLogId: UUID? = null
)

class TaskCompletionPolicyException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

@Service
@Profile("test")
class NoOpCompletionPolicy : CompletionPolicy {
    override fun evaluate(task: Task, now: Instant): CompletionDecision =
        CompletionDecision(requiresPmsUpdate = false)
}
