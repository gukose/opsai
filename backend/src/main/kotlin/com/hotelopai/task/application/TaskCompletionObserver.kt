package com.hotelopai.task.application

import com.hotelopai.task.domain.Task

fun interface TaskCompletionObserver { fun completed(task: Task) }
