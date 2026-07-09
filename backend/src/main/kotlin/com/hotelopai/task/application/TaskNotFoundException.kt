package com.hotelopai.task.application

import java.util.UUID

class TaskNotFoundException(taskId: UUID) : RuntimeException("Task not found: $taskId")
