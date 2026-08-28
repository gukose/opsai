package com.hotelopai.unimock.application.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.unimock.config.UniMockProperties
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.concurrent.atomic.AtomicBoolean

data class DemoTaskResetStatus(val hotelCode:String,val taskCount:Int,val resetAvailable:Boolean=true)
data class DemoTaskResetResult(val hotelCode:String,val tasksDeleted:Int,val relatedRecordsDeleted:Int,val remainingTasks:Int)

class DemoTaskResetException(message:String):RuntimeException(message)
class DemoTaskResetInProgressException:RuntimeException("A demo task reset is already running.")

interface DemoTaskResetPort {
    fun status():DemoTaskResetStatus
    fun reset():DemoTaskResetResult
}

@Service
class HotelOpAiDemoTaskResetClient(
    private val properties:UniMockProperties,
    private val objectMapper:ObjectMapper
):DemoTaskResetPort {
    private fun target(path:String)=demoTargetUri(properties.demoConsole.hotelOpaiBaseUrl,path)

    override fun status():DemoTaskResetStatus = requestStatus(
        RestClient.create().get().uri(target("/api/v1/internal/demo/reset/tasks/status"))
            .header("X-Demo-Pms-Key",properties.demoConsole.sharedKey)
    )

    override fun reset():DemoTaskResetResult {
        requireConfigured()
        val body=RestClient.create().post().uri(target("/api/v1/internal/demo/reset/tasks"))
            .header("X-Demo-Pms-Key",properties.demoConsole.sharedKey)
            .retrieve().onStatus(HttpStatusCode::isError,errorHandler()).body(HotelOpAiResetResponse::class.java)
            ?: throw DemoTaskResetException("Demo tasks could not be reset.")
        return DemoTaskResetResult(properties.demoConsole.hotelCode,body.tasksDeleted,body.relatedRecordsDeleted,body.remainingTasks)
    }

    private fun requestStatus(spec:RestClient.RequestHeadersSpec<*>):DemoTaskResetStatus {
        requireConfigured()
        val body=spec.retrieve().onStatus(HttpStatusCode::isError,errorHandler()).body(DemoTaskResetStatus::class.java)
            ?: throw DemoTaskResetException("Demo task status could not be loaded.")
        if(body.hotelCode!=properties.demoConsole.hotelCode) throw DemoTaskResetException("Hotel OpAI returned an unexpected demo hotel.")
        return body
    }

    private fun requireConfigured() {
        if(properties.demoConsole.hotelOpaiBaseUrl.isBlank() || properties.demoConsole.sharedKey.length<12)
            throw DemoTaskResetException("Demo task reset is not configured.")
        if(properties.demoConsole.hotelCode!="hotel-opai-demo")
            throw DemoTaskResetException("Demo task reset is restricted to hotel-opai-demo.")
    }

    private fun errorHandler()=RestClient.ResponseSpec.ErrorHandler { _,response ->
        val detail=runCatching { objectMapper.readTree(response.body).path("detail").asText() }.getOrNull()?.takeIf(String::isNotBlank)
        throw DemoTaskResetException(when {
            response.statusCode.value()==401 || response.statusCode.value()==403 -> "Demo task reset authentication failed."
            response.statusCode.value()==404 -> "Demo task reset is unavailable in this Hotel OpAI environment."
            response.statusCode.is5xxServerError -> "Hotel OpAI could not reset demo tasks. Please try again."
            else -> detail ?: "Demo tasks could not be reset."
        })
    }

    private data class HotelOpAiResetResponse(val tasksDeleted:Int=0,val relatedRecordsDeleted:Int=0,val remainingTasks:Int=0)
}

@Service
class DemoTaskResetService(
    private val properties:UniMockProperties,
    private val client:DemoTaskResetPort
) {
    private val resetting=AtomicBoolean(false)

    fun status():DemoTaskResetStatus=client.status().also(::requireConfiguredHotel)

    fun reset():DemoTaskResetResult {
        if(!resetting.compareAndSet(false,true)) throw DemoTaskResetInProgressException()
        return try {
            client.reset().also { if(it.hotelCode!=properties.demoConsole.hotelCode) throw DemoTaskResetException("Hotel OpAI returned an unexpected demo hotel.") }
        } finally {
            resetting.set(false)
        }
    }

    private fun requireConfiguredHotel(status:DemoTaskResetStatus) {
        if(status.hotelCode!=properties.demoConsole.hotelCode) throw DemoTaskResetException("Hotel OpAI returned an unexpected demo hotel.")
    }
}
