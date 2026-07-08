package com.hotelopai.auth.application

import java.time.Instant

interface AccessTokenService {
    fun issueToken(context: AccessTokenContext, now: Instant): AccessTokenResult
}
