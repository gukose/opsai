package com.hotelopai.gamification.api
import com.hotelopai.gamification.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
data class AwardRequest(val employeeId:UUID,val sourceReference:String,val completed:Boolean=true,val slaSuccess:Boolean,val qualityScore:Int?,val firstTimeSuccess:Boolean,val vip:Boolean=false,val maintenance:Boolean=false,val guestHappiness:Boolean=false,val teamContribution:Boolean=false)
@RestController @RequestMapping("/api/v1/internal/gamification") class InternalGamificationController(private val service:GamificationService,private val current:CurrentUserContextResolver){
 @PostMapping("/award") @PreAuthorize(PermissionExpressions.GAMIFICATION_VIEW) fun award(@RequestBody r:AwardRequest)=service.award(AwardXpCommand(current.current().hotelId,r.employeeId,r.sourceReference,r.completed,r.slaSuccess,r.qualityScore,r.firstTimeSuccess,r.vip,r.maintenance,r.guestHappiness,r.teamContribution))
 @GetMapping("/leaderboard") @PreAuthorize(PermissionExpressions.GAMIFICATION_VIEW) fun leaderboard(@RequestParam(defaultValue="WEEKLY") period:GamificationPeriod,@RequestParam(required=false) departmentId:UUID?)=service.leaderboard(current.current().hotelId,period,departmentId)
}
