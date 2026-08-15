package com.hotelopai.reporting.api
import com.hotelopai.reporting.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant
data class ManagerQuestionRequest(val question:String,val from:Instant?=null,val to:Instant?=null)
@RestController @RequestMapping("/api/v1/internal/manager-reporting") class InternalManagerReportingController(private val service:ManagerReportingService,private val current:CurrentUserContextResolver){
 @PostMapping @PreAuthorize(PermissionExpressions.MANAGER_REPORTING) fun ask(@RequestBody r:ManagerQuestionRequest):ManagerReport{require(r.question.length in 1..500);val intent=service.interpret(r.question);return service.report(current.current().hotelId,intent,ReportFilter(r.from,r.to))}
}
