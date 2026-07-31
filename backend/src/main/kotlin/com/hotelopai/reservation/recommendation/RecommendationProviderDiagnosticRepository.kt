package com.hotelopai.reservation.recommendation

import java.time.Instant

interface RecommendationProviderDiagnosticRepository {
    fun save(diagnostic: RecommendationProviderDiagnostic): RecommendationProviderDiagnostic
    fun find(id: RecommendationProviderDiagnosticId): RecommendationProviderDiagnostic?
    fun find(filter: RecommendationProviderDiagnosticFilter): RecommendationProviderDiagnosticPage
    fun latest(providerId: RecommendationProviderId): RecommendationProviderDiagnostic?
    fun latestSuccessful(providerId: RecommendationProviderId): RecommendationProviderDiagnostic?
    fun cleanupCompleted(olderThan: Instant, limit: Int): Int
}
