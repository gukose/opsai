package com.hotelopai.employee.domain

enum class EmployeeStatus {
    ACTIVE,
    ON_LEAVE,
    INACTIVE
}

enum class EmployeeOperationalStatus {
    AVAILABLE,
    WORKING,
    BREAK,
    LUNCH,
    MEETING,
    TRAINING,
    OFFLINE,
    ON_LEAVE;

    fun acceptsNormalWork(): Boolean = this == AVAILABLE || this == WORKING
}
