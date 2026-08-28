package com.hotelopai.masterdata

import com.hotelopai.masterdata.application.RoomSearchQuery
import com.hotelopai.masterdata.application.HotelScopedWhere
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RoomSearchQueryTest {
    @Test
    fun `hotel scoped optional predicates omit null values and retain JDBC types`() {
        val hotel=UUID.randomUUID()
        val building=UUID.randomUUID()
        val query=HotelScopedWhere(hotel).equal("building","building_id",null)
        assertThat(query.sql).isEqualTo("hotel_id=:hotel")
        assertThat(query.parameters.hasValue("building")).isFalse()

        query.equal("building","building_id",building)
        assertThat(query.sql).isEqualTo("hotel_id=:hotel and building_id=:building")
        assertThat(query.parameters.getValue("hotel")).isEqualTo(hotel)
        assertThat(query.parameters.getValue("building")).isEqualTo(building)
    }

    @Test
    fun `unfiltered room query contains no untyped null sentinel parameters`() {
        val query=RoomSearchQuery(UUID.randomUUID(),null,null,null,null,null,0,100)
        assertThat(query.where).isEqualTo("hotel_id=:hotel")
        assertThat(query.where).doesNotContain("is null")
    }

    @Test
    fun `building floor and room filters are explicit and hotel scoped`() {
        val building=UUID.randomUUID()
        val floor=UUID.randomUUID()
        val query=RoomSearchQuery(UUID.randomUUID()," 10 ",building,floor,"standard",true,1,25)
        assertThat(query.where).contains(
            "hotel_id=:hotel",
            "lower(room_number) like lower(:roomSearch)",
            "building_id=:building",
            "floor_id=:floor",
            "room_type=:type",
            "active=:active"
        )
        assertThat(query.parameters.getValue("roomSearch")).isEqualTo("%10%")
        assertThat(query.parameters.getValue("type")).isEqualTo("STANDARD")
        assertThat(query.parameters.getValue("offset")).isEqualTo(25)
    }

    @Test
    fun `each optional room filter adds only its own bound predicate`() {
        val hotel=UUID.randomUUID()
        val building=UUID.randomUUID()
        val floor=UUID.randomUUID()
        val cases=listOf(
            RoomSearchQuery(hotel,"101",null,null,null,null,0,25) to "lower(room_number) like lower(:roomSearch)",
            RoomSearchQuery(hotel,null,building,null,null,null,0,25) to "building_id=:building",
            RoomSearchQuery(hotel,null,null,floor,null,null,0,25) to "floor_id=:floor",
            RoomSearchQuery(hotel,null,null,null,"DELUXE",null,0,25) to "room_type=:type",
            RoomSearchQuery(hotel,null,null,null,null,true,0,25) to "active=:active",
            RoomSearchQuery(hotel,null,null,null,null,false,0,25) to "active=:active"
        )
        cases.forEach { (query,predicate) ->
            assertThat(query.where).isEqualTo("hotel_id=:hotel and $predicate")
            assertThat(query.where).doesNotContain("is null")
        }
    }

    @Test
    fun `list and count query use the identical dynamic where clause`() {
        val query=RoomSearchQuery(UUID.randomUUID(),"10",UUID.randomUUID(),null,"standard",false,0,25)
        val list="select * from room_master where ${query.where} order by room_number limit :limit offset :offset"
        val count="select count(*) from room_master where ${query.where}"
        assertThat(list.substringAfter(" where ").substringBefore(" order by")).isEqualTo(query.where)
        assertThat(count.substringAfter(" where ")).isEqualTo(query.where)
    }
}
