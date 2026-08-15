package com.hotelopai.voice.api

import com.hotelopai.voice.application.SpeechToTextProviderException
import com.hotelopai.voice.application.SpeechToTextUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes=[InternalVoiceController::class])
class VoiceApiExceptionHandler {
 @ExceptionHandler(IllegalArgumentException::class) fun invalid()=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Audio request is invalid.")
 @ExceptionHandler(SpeechToTextUnavailableException::class) fun unavailable()=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Speech transcription is unavailable.")
 @ExceptionHandler(SpeechToTextProviderException::class) fun provider()=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,"Speech provider could not transcribe the recording.")
}
