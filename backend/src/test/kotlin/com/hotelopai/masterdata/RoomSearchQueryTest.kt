package com.hotelopai.masterdata

import com.hotelopai.masterdata.application.RoomSearchQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RoomSearchQueryTest {
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
            "lower(room_number) like :roomSearch",
            "building_id=:building",
            "floor_id=:floor",
            "room_type=:type",
            "active=:active"
        )
        assertThat(query.parameters.getValue("roomSearch")).isEqualTo("%10%")
        assertThat(query.parameters.getValue("type")).isEqualTo("STANDARD")
        assertThat(query.parameters.getValue("offset")).isEqualTo(25)
    }
}
