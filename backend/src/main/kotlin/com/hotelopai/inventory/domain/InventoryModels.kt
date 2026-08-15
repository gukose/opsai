package com.hotelopai.inventory.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class InventoryTransactionType { RECEIVE, CONSUME, ADJUST, TRANSFER, MINIBAR_CONSUMPTION, DAMAGE_USAGE }
enum class InventoryUnit { EACH, BOTTLE, PACK, ROLL, LITER, KILOGRAM, SET }
data class InventoryItem(val id:UUID,val hotelId:UUID,val code:String,val name:String,val unit:InventoryUnit,val unitPrice:BigDecimal?,val negativeStockAllowed:Boolean,val active:Boolean)
data class InventoryBalance(val itemId:UUID,val locationId:UUID,val quantity:BigDecimal,val updatedAt:Instant)
data class InventoryTransaction(val id:UUID,val hotelId:UUID,val itemId:UUID,val fromLocationId:UUID?,val toLocationId:UUID?,val type:InventoryTransactionType,val quantity:BigDecimal,val operationalReference:String,val occurredAt:Instant)

data class RecordInventoryTransaction(
    val hotelId:UUID,val itemId:UUID,val fromLocationId:UUID?=null,val toLocationId:UUID?=null,
    val type:InventoryTransactionType,val quantity:BigDecimal,val operationalReference:String,val actorUserId:UUID?,val note:String?=null
) { init { require(quantity > BigDecimal.ZERO); require(operationalReference.isNotBlank()) } }
