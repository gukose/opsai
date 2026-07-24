package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoTokenResponseDto(
    @JsonProperty("access_token")
    val accessToken: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
    @JsonProperty("token_type")
    val tokenType: String? = null,
    val scope: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoPropertyDto(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoPropertyListDto(
    val properties: List<ApaleoPropertyDto> = emptyList(),
    val count: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoUnitDto(
    val id: String? = null,
    val name: String? = null,
    val unitGroup: ApaleoUnitGroupDto? = null,
    val property: ApaleoPropertyDto? = null,
    val status: String? = null,
    val condition: String? = null,
    val description: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoUnitGroupDto(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null,
    val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoUnitListDto(
    val units: List<ApaleoUnitDto> = emptyList(),
    val count: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoBookingListDto(
    val bookings: List<ApaleoBookingDto> = emptyList(),
    val count: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoBookingDto(
    val id: String? = null,
    val booker: ApaleoPersonDto? = null,
    val reservations: List<ApaleoReservationDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoReservationDto(
    val id: String? = null,
    val status: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val unit: ApaleoUnitDto? = null,
    val unitGroup: ApaleoUnitGroupDto? = null,
    val primaryGuest: ApaleoPersonDto? = null,
    val guests: List<ApaleoPersonDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApaleoPersonDto(
    val id: String? = null,
    val firstName: String? = null,
    val middleInitial: String? = null,
    val lastName: String? = null,
    val email: String? = null
)
