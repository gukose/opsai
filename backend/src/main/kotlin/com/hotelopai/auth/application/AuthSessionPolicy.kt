package com.hotelopai.auth.application

import java.time.Duration

interface AuthSessionPolicy {
    fun refreshTokenTtl(): Duration
}
