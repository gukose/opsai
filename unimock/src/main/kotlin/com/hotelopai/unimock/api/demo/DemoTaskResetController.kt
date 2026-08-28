package com.hotelopai.unimock.api.demo

import com.hotelopai.unimock.application.demo.DemoTaskResetException
import com.hotelopai.unimock.application.demo.DemoTaskResetInProgressException
import com.hotelopai.unimock.application.demo.DemoTaskResetService
import com.hotelopai.unimock.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestController
@RequestMapping("/api/demo-tools/task-reset")
class DemoTaskResetController(private val service:DemoTaskResetService) {
    @GetMapping("/status") fun status()=service.status()
    @PostMapping fun reset()=service.reset()
}

@RestControllerAdvice(assignableTypes=[DemoTaskResetController::class])
class DemoTaskResetExceptionHandler {
    @ExceptionHandler(DemoTaskResetInProgressException::class)
    fun concurrent(e:DemoTaskResetInProgressException):ProblemDetail=problem(HttpStatus.CONFLICT,e.message!!)

    @ExceptionHandler(DemoTaskResetException::class)
    fun failure(e:DemoTaskResetException):ProblemDetail=problem(HttpStatus.BAD_GATEWAY,e.message ?: "Demo tasks could not be reset.")

    private fun problem(status:HttpStatus,detail:String)=ProblemDetailFactory.create(status,"Demo task reset failed",detail,URI.create("https://hotelopai.com/problems/unimock-demo-task-reset"))
}
