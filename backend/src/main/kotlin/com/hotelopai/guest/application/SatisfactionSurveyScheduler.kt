package com.hotelopai.guest.application

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component @Profile("local","demo","prod") class SatisfactionSurveyScheduler(private val surveys:SatisfactionSurveyService,private val runner:DistributedScheduledJobRunner,@Value("\${ops.ai.guest.survey.enabled:false}") private val enabled:Boolean){
 @Scheduled(fixedDelayString="\${ops.ai.guest.survey.interval-ms:300000}") fun run(){if(enabled)runner.runSingleton("satisfaction_survey_delivery",Duration.ofMinutes(4)){surveys.sendPending()}}
}
