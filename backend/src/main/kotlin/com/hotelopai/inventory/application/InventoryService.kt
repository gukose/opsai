package com.hotelopai.inventory.application

import com.hotelopai.inventory.domain.*
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class InventoryCategoryRecord(val id:UUID,val code:String,val name:String)
data class InventoryLocationRecord(val id:UUID,val code:String,val name:String,val locationType:String,val active:Boolean)
data class InventoryItemRecord(val id:UUID,val code:String,val name:String,val unit:String,val unitPrice:BigDecimal?,val minimumStock:BigDecimal?,val active:Boolean)
data class UpsertInventoryItem(val id:UUID?=null,val categoryId:UUID?,val code:String,val name:String,val unit:String,val unitPrice:BigDecimal?,val minimumStock:BigDecimal?,val negativeStockAllowed:Boolean=false,val active:Boolean=true)

@Service
class InventoryService(private val jdbc:NamedParameterJdbcTemplate,private val metrics:OperationalObservability,private val clock:Clock=Clock.systemUTC()) {
    @Transactional fun saveCategory(hotelId:UUID,id:UUID?,code:String,name:String):InventoryCategoryRecord {requireSafeMaster(code,name);val resolved=id?:UuidV7Generator.generate(clock.instant());jdbc.update("""insert into inventory_category(id,hotel_id,code,name) values(:id,:hotel,:code,:name) on conflict(id) do update set code=excluded.code,name=excluded.name where inventory_category.hotel_id=:hotel""",mapOf("id" to resolved,"hotel" to hotelId,"code" to code.uppercase(),"name" to name));return InventoryCategoryRecord(resolved,code.uppercase(),name)}
    fun categories(hotelId:UUID)=jdbc.query("select id,code,name from inventory_category where hotel_id=:hotel order by name",mapOf("hotel" to hotelId)){rs,_->InventoryCategoryRecord(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3))}
    @Transactional fun saveLocation(hotelId:UUID,id:UUID?,code:String,name:String,type:String,active:Boolean):InventoryLocationRecord {requireSafeMaster(code,name);require(type.matches(Regex("[A-Z0-9_]{2,40}")));val resolved=id?:UuidV7Generator.generate(clock.instant());jdbc.update("""insert into inventory_location(id,hotel_id,code,name,location_type,active) values(:id,:hotel,:code,:name,:type,:active) on conflict(id) do update set code=excluded.code,name=excluded.name,location_type=excluded.location_type,active=excluded.active where inventory_location.hotel_id=:hotel""",mapOf("id" to resolved,"hotel" to hotelId,"code" to code.uppercase(),"name" to name,"type" to type,"active" to active));return InventoryLocationRecord(resolved,code.uppercase(),name,type,active)}
    fun locations(hotelId:UUID)=jdbc.query("select id,code,name,location_type,active from inventory_location where hotel_id=:hotel order by name",mapOf("hotel" to hotelId)){rs,_->InventoryLocationRecord(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5))}
    @Transactional fun saveItem(hotelId:UUID,c:UpsertInventoryItem):InventoryItemRecord {requireSafeMaster(c.code,c.name);require(runCatching{InventoryUnit.valueOf(c.unit)}.isSuccess){"unsupported inventory unit"};require(c.unitPrice==null||c.unitPrice>=BigDecimal.ZERO);require(c.minimumStock==null||c.minimumStock>=BigDecimal.ZERO);val id=c.id?:UuidV7Generator.generate(clock.instant());val now=clock.instant();jdbc.update("""insert into inventory_item(id,hotel_id,category_id,code,name,unit,unit_price,minimum_stock,negative_stock_allowed,active,created_at,updated_at) values(:id,:hotel,:category,:code,:name,:unit,:price,:minimum,:negative,:active,:now,:now) on conflict(id) do update set category_id=excluded.category_id,code=excluded.code,name=excluded.name,unit=excluded.unit,unit_price=excluded.unit_price,minimum_stock=excluded.minimum_stock,negative_stock_allowed=excluded.negative_stock_allowed,active=excluded.active,updated_at=excluded.updated_at where inventory_item.hotel_id=:hotel""",mapOf("id" to id,"hotel" to hotelId,"category" to c.categoryId,"code" to c.code.uppercase(),"name" to c.name,"unit" to c.unit,"price" to c.unitPrice,"minimum" to c.minimumStock,"negative" to c.negativeStockAllowed,"active" to c.active,"now" to now));return InventoryItemRecord(id,c.code.uppercase(),c.name,c.unit,c.unitPrice,c.minimumStock,c.active)}
    fun items(hotelId:UUID)=jdbc.query("select id,code,name,unit,unit_price,minimum_stock,active from inventory_item where hotel_id=:hotel order by name",mapOf("hotel" to hotelId)){rs,_->InventoryItemRecord(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBoolean(7))}
    fun item(hotelId:UUID,itemId:UUID):InventoryItem = jdbc.query("select * from inventory_item where id=:id and hotel_id=:hotel",mapOf("id" to itemId,"hotel" to hotelId)){rs,_->InventoryItem(rs.getObject("id",UUID::class.java),rs.getObject("hotel_id",UUID::class.java),rs.getString("code"),rs.getString("name"),InventoryUnit.valueOf(rs.getString("unit")),rs.getBigDecimal("unit_price"),rs.getBoolean("negative_stock_allowed"),rs.getBoolean("active"))}.firstOrNull()?:throw NoSuchElementException("Inventory item not found")
    @Transactional
    fun record(c:RecordInventoryTransaction):InventoryTransaction {
        existing(c)?.let { return it }
        val item=jdbc.query("select negative_stock_allowed,unit_price from inventory_item where id=:item and hotel_id=:hotel",mapOf("item" to c.itemId,"hotel" to c.hotelId)){rs,_->rs.getBoolean(1) to rs.getBigDecimal(2)}.firstOrNull()
            ?: throw NoSuchElementException("Inventory item not found")
        when(c.type){
            InventoryTransactionType.RECEIVE -> requireNotNull(c.toLocationId)
            InventoryTransactionType.CONSUME,InventoryTransactionType.MINIBAR_CONSUMPTION,InventoryTransactionType.DAMAGE_USAGE -> requireNotNull(c.fromLocationId)
            InventoryTransactionType.TRANSFER -> { requireNotNull(c.fromLocationId); requireNotNull(c.toLocationId); require(c.fromLocationId!=c.toLocationId) }
            InventoryTransactionType.ADJUST -> require(c.fromLocationId!=null || c.toLocationId!=null)
        }
        c.fromLocationId?.let { location -> changeBalance(c.hotelId,c.itemId,location,c.quantity.negate(),item.first) }
        c.toLocationId?.let { location -> changeBalance(c.hotelId,c.itemId,location,c.quantity,item.first) }
        val now=clock.instant(); val id=UuidV7Generator.generate(now)
        jdbc.update("""insert into inventory_transaction(id,hotel_id,item_id,from_location_id,to_location_id,transaction_type,quantity,unit_price,operational_reference,note,actor_user_id,occurred_at,created_at)
            values(:id,:hotel,:item,:from,:to,:type,:quantity,:price,:ref,:note,:actor,:now,:now)""",mapOf("id" to id,"hotel" to c.hotelId,"item" to c.itemId,"from" to c.fromLocationId,"to" to c.toLocationId,"type" to c.type.name,"quantity" to c.quantity,"price" to item.second,"ref" to c.operationalReference,"note" to c.note,"actor" to c.actorUserId,"now" to now))
        metrics.incrementCounter("hotelopai.inventory.transaction.total","operation" to c.type.name.lowercase(),"outcome" to "success")
        return InventoryTransaction(id,c.hotelId,c.itemId,c.fromLocationId,c.toLocationId,c.type,c.quantity,c.operationalReference,now)
    }

    fun balances(hotelId:UUID)=jdbc.query("""select b.item_id,b.location_id,b.quantity,b.updated_at from inventory_balance b join inventory_item i on i.id=b.item_id where b.hotel_id=:hotel and i.hotel_id=:hotel order by i.name""",mapOf("hotel" to hotelId)){rs,_->InventoryBalance(rs.getObject(1,UUID::class.java),rs.getObject(2,UUID::class.java),rs.getBigDecimal(3),rs.getTimestamp(4).toInstant())}

    private fun changeBalance(hotelId:UUID,itemId:UUID,locationId:UUID,delta:BigDecimal,negativeAllowed:Boolean){
        jdbc.update("""insert into inventory_balance(hotel_id,item_id,location_id,quantity,version,updated_at) values(:hotel,:item,:location,0,0,:now) on conflict do nothing""",mapOf("hotel" to hotelId,"item" to itemId,"location" to locationId,"now" to clock.instant()))
        val current=jdbc.queryForObject("select quantity from inventory_balance where hotel_id=:hotel and item_id=:item and location_id=:location for update",mapOf("hotel" to hotelId,"item" to itemId,"location" to locationId),BigDecimal::class.java)!!
        val next=current+delta; require(negativeAllowed || next>=BigDecimal.ZERO){"Inventory transaction would create negative stock"}
        jdbc.update("update inventory_balance set quantity=:next,version=version+1,updated_at=:now where hotel_id=:hotel and item_id=:item and location_id=:location",mapOf("next" to next,"now" to clock.instant(),"hotel" to hotelId,"item" to itemId,"location" to locationId))
    }
    private fun existing(c:RecordInventoryTransaction)=jdbc.query("select id,occurred_at from inventory_transaction where hotel_id=:hotel and operational_reference=:ref and item_id=:item and transaction_type=:type",mapOf("hotel" to c.hotelId,"ref" to c.operationalReference,"item" to c.itemId,"type" to c.type.name)){rs,_->InventoryTransaction(rs.getObject(1,UUID::class.java),c.hotelId,c.itemId,c.fromLocationId,c.toLocationId,c.type,c.quantity,c.operationalReference,rs.getTimestamp(2).toInstant())}.firstOrNull()
    private fun requireSafeMaster(code:String,name:String){require(code.matches(Regex("[A-Za-z0-9_-]{2,64}")));require(name.isNotBlank()&&name.length<=160)}
}
