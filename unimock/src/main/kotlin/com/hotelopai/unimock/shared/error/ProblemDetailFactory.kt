package com.hotelopai.unimock.shared.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import java.net.URI

object ProblemDetailFactory {
    fun create(
        status: HttpStatus,
        title: String,
        detail: String,
        type: URI
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            this.type = type
        }
}
