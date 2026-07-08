package com.hotelopai.workflow.domain

class WorkflowStateMachine<S>(
    private val allowedTransitions: Map<S, Set<S>>
) {
    fun canTransition(from: S, to: S): Boolean =
        to in allowedTargets(from)

    fun allowedTargets(from: S): Set<S> =
        allowedTransitions[from].orEmpty()

    fun requireTransition(from: S, to: S) {
        require(canTransition(from, to)) {
            "Invalid workflow transition from $from to $to"
        }
    }
}
