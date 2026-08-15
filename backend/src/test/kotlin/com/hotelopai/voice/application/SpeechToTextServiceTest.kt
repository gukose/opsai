package com.hotelopai.voice.application

import com.hotelopai.assistant.application.OperationalIntent
import com.hotelopai.assistant.application.OperationalIntentService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SpeechToTextServiceTest {
    private val internal = InternalDemoSpeechToTextProvider()
    private val service = SpeechToTextService(listOf(internal), VoiceProperties(), OperationalIntentService())

    @Test fun `fixture transcription is explicitly simulated and produces operational proposal`() {
        val result=service.transcribeFixture("hvac-room-502","en")
        assertThat(result.transcript.simulated).isTrue()
        assertThat(result.transcript.retentionApplied).isTrue()
        assertThat(result.intent.intent).isEqualTo(OperationalIntent.MAINTENANCE_REQUEST)
        assertThat(result.intent.entities.requiredSkill).isEqualTo("HVAC")
    }

    @Test fun `internal demo never fakes arbitrary audio transcription`() {
        assertThatThrownBy { service.transcribeAudio(AudioTranscriptionInput(byteArrayOf(1,2,3),"audio/mp4","recording.m4a","en")) }
            .isInstanceOf(SpeechToTextUnavailableException::class.java)
            .hasMessageContaining("not transcribed")
    }

    @Test fun `audio validation rejects empty oversized and unsupported input`() {
        assertThatThrownBy { service.transcribeAudio(AudioTranscriptionInput(byteArrayOf(),"audio/mp4","empty.m4a",null)) }.isInstanceOf(IllegalArgumentException::class.java)
        val limited=SpeechToTextService(listOf(internal),VoiceProperties(maximumAudioBytes=2),OperationalIntentService())
        assertThatThrownBy { limited.transcribeAudio(AudioTranscriptionInput(byteArrayOf(1,2,3),"audio/mp4","large.m4a",null)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.transcribeAudio(AudioTranscriptionInput(byteArrayOf(1),"text/plain","bad.txt",null)) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
