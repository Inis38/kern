package com.kern.web.api

import com.kern.application.AppsService
import com.kern.web.dto.AppConfigFieldResponse
import com.kern.web.dto.AppDetailResponse
import com.kern.web.dto.AppOverviewResponse
import com.kern.web.dto.OperationResponse
import com.kern.web.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/apps")
class AppsApiController(
    private val appsService: AppsService,
) {

    @GetMapping
    fun list(): List<AppOverviewResponse> =
        appsService.list().map { it.toResponse() }

    @GetMapping("/{slug}")
    fun detail(@PathVariable slug: String): AppDetailResponse {
        val app = appsService.get(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Приложение не найдено")
        val status = app.status()
        return AppDetailResponse(
            slug = slug,
            displayName = app.id.displayName,
            description = app.description,
            status = status.toResponse(slug, app.id.displayName, app.description),
            config = app.loadConfig().map { it.toResponse() },
        )
    }

    @PostMapping("/{slug}/install")
    fun install(@PathVariable slug: String): OperationResponse =
        appsService.install(slug).toResponse()

    @GetMapping("/{slug}/config")
    fun config(@PathVariable slug: String): List<AppConfigFieldResponse> =
        appsService.loadConfig(slug).map { it.toResponse() }

    @PutMapping("/{slug}/config")
    fun saveConfig(
        @PathVariable slug: String,
        @RequestBody body: Map<String, String>,
    ): OperationResponse =
        appsService.saveConfig(slug, body).toResponse()
}
