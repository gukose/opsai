package com.hotelopai.pms.application

data class PmsCapabilities(
    val hotelLookup: Boolean = false,
    val roomListing: Boolean = false,
    val roomStatusLookup: Boolean = false,
    val roomStatusUpdate: Boolean = false,
    val stayLookup: Boolean = false,
    val reservationLookup: Boolean = false,
    val guestLookup: Boolean = false,
    val assetLookup: Boolean = false,
    val issueTypeLookup: Boolean = false,
    val housekeepingStatusUpdate: Boolean = false,
    val maintenanceUpdate: Boolean = false,
    val eventRetrieval: Boolean = false,
    val eventCreation: Boolean = false,
    val webhooks: Boolean = false,
    val incrementalSync: Boolean = false
) {
    fun supports(capability: PmsCapability): Boolean =
        when (capability) {
            PmsCapability.HOTEL_LOOKUP -> hotelLookup
            PmsCapability.ROOM_LISTING -> roomListing
            PmsCapability.ROOM_STATUS_LOOKUP -> roomStatusLookup
            PmsCapability.ROOM_STATUS_UPDATE -> roomStatusUpdate
            PmsCapability.STAY_LOOKUP -> stayLookup
            PmsCapability.RESERVATION_LOOKUP -> reservationLookup
            PmsCapability.GUEST_LOOKUP -> guestLookup
            PmsCapability.ASSET_LOOKUP -> assetLookup
            PmsCapability.ISSUE_TYPE_LOOKUP -> issueTypeLookup
            PmsCapability.HOUSEKEEPING_STATUS_UPDATE -> housekeepingStatusUpdate
            PmsCapability.MAINTENANCE_UPDATE -> maintenanceUpdate
            PmsCapability.EVENT_RETRIEVAL -> eventRetrieval
            PmsCapability.EVENT_CREATION -> eventCreation
            PmsCapability.WEBHOOKS -> webhooks
            PmsCapability.INCREMENTAL_SYNC -> incrementalSync
        }

    fun validateConsistency(providerId: String): List<String> = buildList {
        if (roomStatusUpdate && !roomStatusLookup) {
            add("Provider '$providerId' declares room status update without room status lookup.")
        }
        if (maintenanceUpdate && (!roomListing || !issueTypeLookup)) {
            add("Provider '$providerId' declares maintenance update without room listing and issue type lookup.")
        }
        if (housekeepingStatusUpdate && !stayLookup) {
            add("Provider '$providerId' declares housekeeping status update without stay lookup.")
        }
        if (incrementalSync && !eventRetrieval) {
            add("Provider '$providerId' declares incremental sync without event retrieval.")
        }
    }
}

enum class PmsCapability {
    HOTEL_LOOKUP,
    ROOM_LISTING,
    ROOM_STATUS_LOOKUP,
    ROOM_STATUS_UPDATE,
    STAY_LOOKUP,
    RESERVATION_LOOKUP,
    GUEST_LOOKUP,
    ASSET_LOOKUP,
    ISSUE_TYPE_LOOKUP,
    HOUSEKEEPING_STATUS_UPDATE,
    MAINTENANCE_UPDATE,
    EVENT_RETRIEVAL,
    EVENT_CREATION,
    WEBHOOKS,
    INCREMENTAL_SYNC
}

data class PmsProviderMetadata(
    val id: String,
    val displayName: String,
    val capabilities: PmsCapabilities,
    val readiness: PmsProviderReadiness
)

data class PmsProviderReadiness(
    val configured: Boolean,
    val enabled: Boolean,
    val message: String? = null
)

class UnsupportedPmsCapabilityException(
    providerId: String,
    capability: PmsCapability
) : RuntimeException("PMS provider '$providerId' does not support required capability '$capability'.")
