package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskTimingCalculatorTest {
    private val id=UUID.randomUUID(); private val hotel=UUID.randomUUID()
    private fun e(status:TaskStatus,time:String,op:TaskTransition)=TaskStateHistoryEntry(id,hotel,null,status,op,occurredAt=Instant.parse(time+"Z"))
    @Test fun `calculates productive and pause segments`() {
        val h=listOf(e(TaskStatus.STARTED,"2026-01-01T09:00:00",TaskTransition.START),e(TaskStatus.WAITING,"2026-01-01T09:20:00",TaskTransition.PAUSE),e(TaskStatus.IN_PROGRESS,"2026-01-01T09:30:00",TaskTransition.RESUME),e(TaskStatus.COMPLETED,"2026-01-01T09:50:00",TaskTransition.COMPLETE))
        val t=TaskTimingCalculator.calculate(h,Instant.parse("2026-01-01T09:50:00Z")); assertThat(t.totalPauseDurationSeconds).isEqualTo(600); assertThat(t.actualWorkingDurationSeconds).isEqualTo(2400)
    }
}
