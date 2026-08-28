package com.hotelopai.masterdata

import com.hotelopai.masterdata.api.MasterDataAdminController
import com.hotelopai.masterdata.application.MasterDataAdminService
import com.hotelopai.masterdata.application.PageView
import com.hotelopai.shared.security.CurrentUserContext
import com.hotelopai.shared.security.CurrentUserContextResolver
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class MasterDataAdminControllerListTest {
    private val service=mock(MasterDataAdminService::class.java)
    private val current=mock(CurrentUserContextResolver::class.java)
    private val hotelId=UUID.randomUUID()
    private val userId=UUID.randomUUID()
    private val mvc=MockMvcBuilders.standaloneSetup(MasterDataAdminController(service,current)).build()

    @Test
    fun `default administration list mappings return 200 when queries succeed`() {
        `when`(current.current()).thenReturn(CurrentUserContext(userId,hotelId,UUID.randomUUID(),emptySet(),emptySet()))
        `when`(service.hotelsFor(userId,false)).thenReturn(emptyList())
        `when`(service.buildings(hotelId)).thenReturn(emptyList())
        `when`(service.floors(hotelId,null)).thenReturn(emptyList())
        `when`(service.rooms(hotelId,null,null,null,null,null,0,25)).thenReturn(PageView(emptyList(),0,25,0))
        `when`(service.memberships(hotelId)).thenReturn(emptyList())
        `when`(service.namedList("department",hotelId)).thenReturn(emptyList())
        `when`(service.namedList("role",hotelId)).thenReturn(emptyList())
        `when`(service.namedList("skill",hotelId)).thenReturn(emptyList())
        `when`(service.shifts(hotelId)).thenReturn(emptyList())

        mvc.get("/api/v1/internal/admin/hotels").andExpect { status { isOk() } }
        listOf("departments","buildings","floors","rooms","employees","roles","skills","shifts").forEach { section ->
            mvc.get("/api/v1/internal/admin/hotels/$hotelId/$section").andExpect { status { isOk() } }
        }
    }
}
