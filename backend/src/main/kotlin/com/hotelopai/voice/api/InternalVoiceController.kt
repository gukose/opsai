package com.hotelopai.voice.api
import com.hotelopai.voice.application.*
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.MediaType
data class InternalDemoTranscriptionRequest(val fixtureKey:String,val languageHint:String?=null)
@RestController @RequestMapping("/api/v1/internal/voice") class InternalVoiceController(private val service:SpeechToTextService){
 @PostMapping("/internal-demo/transcribe") @PreAuthorize(PermissionExpressions.ASSISTANT_USE) fun transcribe(@RequestBody r:InternalDemoTranscriptionRequest)=service.transcribeFixture(r.fixtureKey,r.languageHint)
 @PostMapping("/transcribe",consumes=[MediaType.MULTIPART_FORM_DATA_VALUE]) @PreAuthorize(PermissionExpressions.ASSISTANT_USE)
 fun transcribeAudio(@RequestPart("audio") audio:MultipartFile,@RequestPart("languageHint",required=false) languageHint:String?)=service.transcribeAudio(AudioTranscriptionInput(audio.bytes,audio.contentType?.lowercase()?:"application/octet-stream",audio.originalFilename?.take(128)?:"recording",languageHint))
}
