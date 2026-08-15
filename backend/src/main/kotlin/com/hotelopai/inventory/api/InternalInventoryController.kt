package com.hotelopai.inventory.api

import com.hotelopai.inventory.application.InventoryService
import com.hotelopai.inventory.domain.*
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
import com.hotelopai.inventory.application.UpsertInventoryItem
data class InventoryCategoryRequest(val id:UUID?=null,val code:String,val name:String)
data class InventoryLocationRequest(val id:UUID?=null,val code:String,val name:String,val locationType:String,val active:Boolean=true)

data class InventoryTransactionRequest(val itemId:UUID,val fromLocationId:UUID?=null,val toLocationId:UUID?=null,val type:InventoryTransactionType,val quantity:BigDecimal,val operationalReference:String,val note:String?=null)
@RestController @RequestMapping("/api/v1/internal/inventory")
class InternalInventoryController(private val service:InventoryService,private val current:CurrentUserContextResolver){
 @GetMapping("/categories") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun categories()=service.categories(current.current().hotelId)
 @PostMapping("/categories") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun category(@RequestBody r:InventoryCategoryRequest)=service.saveCategory(current.current().hotelId,r.id,r.code,r.name)
 @GetMapping("/locations") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun locations()=service.locations(current.current().hotelId)
 @PostMapping("/locations") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun location(@RequestBody r:InventoryLocationRequest)=service.saveLocation(current.current().hotelId,r.id,r.code,r.name,r.locationType,r.active)
 @GetMapping("/items") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun items()=service.items(current.current().hotelId)
 @PostMapping("/items") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun item(@RequestBody r:UpsertInventoryItem)=service.saveItem(current.current().hotelId,r)
 @GetMapping("/balances") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun balances()=service.balances(current.current().hotelId)
 @PostMapping("/transactions") @PreAuthorize(PermissionExpressions.INVENTORY_OPERATIONS) fun record(@RequestBody r:InventoryTransactionRequest)=service.record(RecordInventoryTransaction(current.current().hotelId,r.itemId,r.fromLocationId,r.toLocationId,r.type,r.quantity,r.operationalReference,current.current().userId,r.note))
}
