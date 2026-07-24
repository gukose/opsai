package com.hotelopai.shared.api

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeprecatedApi(
    val sunset: String = "",
    val link: String = "",
    val message: String = ""
)
