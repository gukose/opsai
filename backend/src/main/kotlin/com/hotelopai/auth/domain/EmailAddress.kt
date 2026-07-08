package com.hotelopai.auth.domain

@JvmInline
value class EmailAddress private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "email must not be blank" }
        require(value.contains("@")) { "email must contain @" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): EmailAddress = EmailAddress(value.trim().lowercase())
    }
}
