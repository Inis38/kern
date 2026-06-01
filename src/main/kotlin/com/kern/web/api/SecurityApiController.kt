package com.kern.web.api

import com.kern.application.SecurityService
import com.kern.web.dto.SecurityResponse
import com.kern.web.dto.toResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class SecurityApiController(
    private val securityService: SecurityService,
) {

    @GetMapping("/security")
    fun security(): SecurityResponse = securityService.overview().toResponse()
}
