package com.hotelopai.task.api

import com.hotelopai.task.application.TaskLifecycleService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val taskLifecycleService: TaskLifecycleService
) {
    @PostMapping
    fun createTask(@RequestBody request: CreateTaskRequest): TaskResponse =
        TaskResponse.from(taskLifecycleService.createTask(request.toCommand()))

    @GetMapping("/{taskId}")
    fun getTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.getTask(taskId))

    @GetMapping
    fun listTasks(): List<TaskResponse> =
        taskLifecycleService.listTasks().map(TaskResponse::from)

    @PostMapping("/{taskId}/assign")
    fun assignTask(
        @PathVariable taskId: String,
        @RequestBody request: AssignTaskRequest
    ): TaskResponse =
        TaskResponse.from(taskLifecycleService.assignTask(taskId, request.toCommand()))

    @PostMapping("/{taskId}/start")
    fun startTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.startTask(taskId))

    @PostMapping("/{taskId}/pause")
    fun pauseTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.pauseTask(taskId))

    @PostMapping("/{taskId}/resume")
    fun resumeTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.resumeTask(taskId))

    @PostMapping("/{taskId}/complete")
    fun completeTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.completeTask(taskId))

    @PostMapping("/{taskId}/cancel")
    fun cancelTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.cancelTask(taskId))

    @PostMapping("/{taskId}/overdue")
    fun overdueTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.markOverdue(taskId))
}
