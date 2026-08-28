package com.hotelopai.unimock.api.demo

import com.hotelopai.unimock.application.demo.DemoEventDeliveryException
import com.hotelopai.unimock.application.demo.DemoEventRequest
import com.hotelopai.unimock.application.demo.PmsDemoConsoleService
import com.hotelopai.unimock.config.UniMockProperties
import com.hotelopai.unimock.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/api/demo-console")
class PmsDemoConsoleController(private val service:PmsDemoConsoleService,private val properties:UniMockProperties) {
    @GetMapping("/config") fun config()=mapOf("hotelCode" to properties.demoConsole.hotelCode,"hotelName" to "Hotel OpAI Demo")
    @GetMapping("/rooms") fun rooms()=service.rooms().map { mapOf("roomNumber" to it.roomNumber,"status" to it.status) }
    @GetMapping("/events") fun events()=service.events()
    @PostMapping("/events") fun send(@RequestBody request:DemoEventRequest)=service.send(request)
}

@RestControllerAdvice(assignableTypes=[PmsDemoConsoleController::class])
class PmsDemoConsoleExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(e:IllegalArgumentException):ProblemDetail=ProblemDetailFactory.create(HttpStatus.BAD_REQUEST,"Invalid event data",e.message ?: "Invalid event data",URI.create("https://hotelopai.com/problems/unimock-invalid-demo-event"))
    @ExceptionHandler(DemoEventDeliveryException::class)
    fun delivery(e:DemoEventDeliveryException):ProblemDetail=ProblemDetailFactory.create(HttpStatus.BAD_GATEWAY,"Delivery failed",e.message ?: "Event could not be delivered",URI.create("https://hotelopai.com/problems/unimock-demo-delivery-failed"))
}
