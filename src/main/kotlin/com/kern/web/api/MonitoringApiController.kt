package com.kern.web.api

import com.kern.application.MonitoringService
import com.kern.web.dto.MonitoringResponse
import com.kern.web.dto.toResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class MonitoringApiController(
    private val monitoringService: MonitoringService,
) {

    @GetMapping("/monitoring")
    fun monitoring(): MonitoringResponse =
        monitoringService.snapshot().toResponse()
}
