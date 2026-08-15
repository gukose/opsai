package com.hotelopai.pms.domain

import java.math.BigDecimal
import java.time.Instant

data class FolioChargeRequest(val roomNumber:String,val amount:BigDecimal,val currency:String,val description:String,val idempotencyKey:String) {
    init { require(amount >= BigDecimal.ZERO); require(currency.matches(Regex("[A-Z]{3}"))); require(idempotencyKey.isNotBlank()) }
}
data class FolioChargeResult(val success:Boolean,val providerReference:String?,val retryable:Boolean,val occurredAt:Instant,val failureCategory:String?=null)
