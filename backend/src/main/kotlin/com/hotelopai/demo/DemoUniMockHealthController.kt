package com.hotelopai.demo

import com.hotelopai.integration.unimock.RestUniMockClient
import com.hotelopai.integration.unimock.UniMockHealthProbe
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
@Profile("!prod & (demo | local | test)")
@RequestMapping("/api/v1/internal/demo/unimock")
@PreAuthorize("@permissionGuard.hasRole('ADMIN')")
class DemoUniMockHealthController(
    private val uniMockClient: RestUniMockClient
) {
    @GetMapping("/health")
    fun health(): UniMockHealthProbe = uniMockClient.probeHealth()
}
