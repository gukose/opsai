package com.hotelopai.voice.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.OperationalIntentResult
import com.hotelopai.assistant.application.OperationalIntentService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

data class SpeechTranscript(val transcript:String,val languageCode:String,val confidence:Double,val provider:String,val simulated:Boolean,val retentionApplied:Boolean)
data class AudioTranscriptionInput(val bytes:ByteArray,val contentType:String,val originalFileName:String,val languageHint:String?)
interface SpeechToTextProvider {
    val id:String
    fun transcribeFixture(fixtureKey:String,languageHint:String?):SpeechTranscript = throw UnsupportedOperationException("Fixture transcription is unsupported")
    fun transcribeAudio(input:AudioTranscriptionInput):SpeechTranscript = throw UnsupportedOperationException("Audio transcription is unsupported")
}

@Component class InternalDemoSpeechToTextProvider:SpeechToTextProvider{
    override val id="internal-demo"
    override fun transcribeFixture(fixtureKey:String,languageHint:String?)=when(fixtureKey){
        "hvac-room-502"->SpeechTranscript("Room 502 air conditioning is not cooling",languageHint?:"en",0.98,id,true,true)
        "minibar-room-203"->SpeechTranscript("Room 203 minibar two waters consumed",languageHint?:"en",0.97,id,true,true)
        else->throw IllegalArgumentException("Unknown deterministic STT fixture")
    }
    override fun transcribeAudio(input:AudioTranscriptionInput):SpeechTranscript =
        throw SpeechToTextUnavailableException("InternalDemo accepts fixture keys only; real audio was not transcribed")
}

@ConfigurationProperties("ops.ai.voice")
data class VoiceProperties(
    val activeProvider:String="internal-demo",
    val maximumAudioBytes:Int=10_000_000,
    val external:ExternalSpeechProperties=ExternalSpeechProperties()
)
data class ExternalSpeechProperties(
    val enabled:Boolean=false,
    val endpoint:String="https://api.openai.com/v1/audio/transcriptions",
    val model:String="gpt-4o-mini-transcribe",
    val credentialReference:String="env:OPENAI_API_KEY",
    val allowedProfiles:Set<String> = setOf("demo"),
    val timeout:Duration=Duration.ofSeconds(45)
)

@Component
class ExternalHttpSpeechToTextProvider(
    private val properties:VoiceProperties,
    private val environment:Environment,
    private val objectMapper:ObjectMapper
):SpeechToTextProvider {
    override val id="external"
    override fun transcribeAudio(input:AudioTranscriptionInput):SpeechTranscript {
        val config=properties.external
        if(!config.enabled) throw SpeechToTextUnavailableException("External speech transcription is disabled")
        if(environment.activeProfiles.none { it in config.allowedProfiles }) throw SpeechToTextUnavailableException("External speech transcription is not allowed for the active profile")
        val endpoint=URI.create(config.endpoint)
        require(endpoint.scheme=="https") { "External speech endpoint must use HTTPS" }
        val key=resolveCredential(config.credentialReference) ?: throw SpeechToTextUnavailableException("External speech credential is unavailable")
        val boundary="hotelopai-${UUID.randomUUID()}"
        val body=multipart(boundary,input,config.model)
        val request=HttpRequest.newBuilder(endpoint).timeout(config.timeout)
            .header("Authorization","Bearer $key")
            .header("Content-Type","multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        val response=HttpClient.newBuilder().connectTimeout(config.timeout).build().send(request,HttpResponse.BodyHandlers.ofString())
        if(response.statusCode() !in 200..299) throw SpeechToTextProviderException("Speech provider request failed with status ${response.statusCode()}")
        val json=runCatching{objectMapper.readTree(response.body())}.getOrElse{throw SpeechToTextProviderException("Speech provider returned malformed JSON")}
        val text=json.path("text").asText().trim()
        if(text.isBlank()) throw SpeechToTextProviderException("Speech provider returned an empty transcript")
        val language=json.path("language").asText(input.languageHint?:"und").take(16)
        return SpeechTranscript(text.take(4000),language,0.85,id,false,true)
    }
    private fun resolveCredential(reference:String):String? = reference.removePrefix("env:").takeIf{reference.startsWith("env:")}?.let(System::getenv)?.takeIf{it.isNotBlank()}
    private fun multipart(boundary:String,input:AudioTranscriptionInput,model:String):ByteArray {
        val prefix=("--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n$model\r\n"+
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.${extension(input.contentType)}\"\r\nContent-Type: ${input.contentType}\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        val suffix="\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        return prefix+input.bytes+suffix
    }
    private fun extension(contentType:String)=when(contentType){"audio/wav"->"wav";"audio/mpeg"->"mp3";"audio/mp4","audio/m4a","audio/x-m4a"->"m4a";"audio/webm"->"webm";else->"audio"}
}

data class TranscriptionProposal(val transcript:SpeechTranscript,val intent:OperationalIntentResult)
@Service
@EnableConfigurationProperties(VoiceProperties::class)
class SpeechToTextService(providers:List<SpeechToTextProvider>,private val properties:VoiceProperties,private val intents:OperationalIntentService){
    private val byId=providers.associateBy{it.id}
    fun transcribeFixture(key:String,languageHint:String?)=requireNotNull(byId["internal-demo"]).transcribeFixture(key,languageHint).toProposal()
    fun transcribeAudio(input:AudioTranscriptionInput):TranscriptionProposal {
        require(input.bytes.isNotEmpty()){ "Audio file is empty" }
        require(input.bytes.size<=properties.maximumAudioBytes){ "Audio file exceeds configured limit" }
        require(input.contentType.lowercase() in SUPPORTED_CONTENT_TYPES){ "Audio content type is unsupported" }
        return (byId[properties.activeProvider]?:throw SpeechToTextUnavailableException("Configured speech provider is unavailable")).transcribeAudio(input).toProposal()
    }
    private fun SpeechTranscript.toProposal()=TranscriptionProposal(this,intents.interpret(transcript,languageCode))
    companion object { val SUPPORTED_CONTENT_TYPES=setOf("audio/wav","audio/mpeg","audio/mp4","audio/m4a","audio/x-m4a","audio/webm","audio/aac","audio/3gpp") }
}
class SpeechToTextUnavailableException(message:String):RuntimeException(message)
class SpeechToTextProviderException(message:String):RuntimeException(message)
