package com.hotelopai.pms.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class PmsDomainModelTest {
    @Test
    fun `provider-independent models do not expose vendor-specific fields`() {
        val modelClasses = listOf(
            PmsHotel::class,
            PmsReservation::class,
            PmsGuest::class,
            PmsRoom::class,
            PmsRoomStatus::class,
            PmsStay::class,
            PmsHousekeepingTask::class,
            PmsAsset::class,
            PmsIssueType::class,
            PmsEvent::class
        )

        val vendorTerms = listOf("unimock", "mews", "apaleo", "opera", "protel", "cloudbeds")
        val fieldNames = modelClasses.flatMap { model ->
            model.memberProperties.map { "${model.simpleName}.${it.name}" }
        }

        assertThat(fieldNames)
            .noneMatch { field -> vendorTerms.any { field.contains(it, ignoreCase = true) } }
    }
}
