package com.hotelopai.vision.api

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.hotelopai.assistant.api.AssistantConversationResponse
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import com.hotelopai.vision.application.ImportVisionAnalysisCommand
import com.hotelopai.vision.application.VisionAnalysisImportService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/assistant/conversations")
class VisionAnalysisImportController(
    private val visionAnalysisImportService: VisionAnalysisImportService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping("/{conversationId}/vision-analyses/{analysisId}/import")
    @PreAuthorize(PermissionExpressions.ASSISTANT_VISION_IMPORT)
    fun importVisionAnalysis(
        @PathVariable conversationId: String,
        @PathVariable analysisId: UUID,
        @RequestBody(required = false) request: ImportVisionAnalysisRequest?
    ): AssistantConversationResponse {
        request?.rejectClientSuppliedFields()
        return currentUserContextResolver.current().let { currentUser ->
            AssistantConversationResponse.from(
                visionAnalysisImportService.importCompletedAnalysis(
                    ImportVisionAnalysisCommand(
                        conversationId = conversationId,
                        analysisId = analysisId,
                        hotelId = currentUser.hotelId.toString(),
                        userId = currentUser.userId.toString()
                    )
                )
            )
        }
    }
}

data class ImportVisionAnalysisRequest(
    val hotelId: String? = null,
    val userId: String? = null,
    val attachmentId: String? = null,
    val observationText: String? = null,
    val text: String? = null,
    val confidence: Double? = null,
    val providerMetadata: Map<String, Any?>? = null,
    val providerId: String? = null,
    val storageReference: String? = null,
    val localReference: String? = null,
    val localUri: String? = null,
    val fileUri: String? = null,
    val deviceUri: String? = null,
    val mediaUrl: String? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val base64: String? = null,
    val binary: String? = null,
    val rawBytes: String? = null
) {
    @JsonIgnore
    private val unsupportedFields: MutableSet<String> = linkedSetOf()

    @JsonAnySetter
    fun captureUnsupportedField(name: String, value: Any?) {
        unsupportedFields += name
    }

    fun rejectClientSuppliedFields() {
        require(unsupportedFields.isEmpty()) {
            "unsupported vision analysis import fields are not accepted: ${unsupportedFields.joinToString(", ")}"
        }
        require(
            hotelId == null &&
                userId == null &&
                attachmentId == null &&
                observationText == null &&
                text == null &&
                confidence == null &&
                providerMetadata == null &&
                providerId == null
        ) {
            "client-supplied vision analysis data is not accepted"
        }
        require(
            storageReference == null &&
                localReference == null &&
                localUri == null &&
                fileUri == null &&
                deviceUri == null &&
                mediaUrl == null &&
                imageUrl == null &&
                fileUrl == null
        ) {
            "vision analysis media and storage fields are not accepted"
        }
        require(base64 == null && binary == null && rawBytes == null) {
            "raw vision analysis media is not accepted"
        }
    }
}
