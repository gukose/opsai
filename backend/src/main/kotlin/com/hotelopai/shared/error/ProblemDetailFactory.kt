package com.hotelopai.shared.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import java.net.URI

object ProblemDetailFactory {
    fun create(
        status: HttpStatus,
        title: String,
        detail: String,
        type: URI? = null,
        instance: URI? = null
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            type?.let { this.type = it }
            instance?.let { this.instance = it }
        }
}
