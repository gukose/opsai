package com.hotelopai.vision.infrastructure.unavailable

import com.hotelopai.vision.application.VisionAnalysisPort
import com.hotelopai.vision.application.VisionAnalysisProviderUnavailableException
import com.hotelopai.vision.application.VisionAnalysisRequest
import com.hotelopai.vision.domain.VisionAnalysisResult

object UnavailableVisionAnalysisProvider : VisionAnalysisPort {
    override fun analyze(request: VisionAnalysisRequest): VisionAnalysisResult {
        throw VisionAnalysisProviderUnavailableException("Vision analysis provider is unavailable")
    }
}
