package com.kern.web

import com.kern.application.MonitoringService
import com.kern.config.MonitoringProperties
import com.kern.web.dto.toResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(
    private val monitoringService: MonitoringService,
    private val monitoringProperties: MonitoringProperties,
) {

    @GetMapping("/")
    fun dashboard(model: Model): String {
        val snapshot = monitoringService.snapshot()
        val response = snapshot.toResponse()
        model.addAttribute("data", response)
        model.addAttribute("refreshIntervalMs", monitoringProperties.refreshIntervalMs)
        return "dashboard"
    }
}
