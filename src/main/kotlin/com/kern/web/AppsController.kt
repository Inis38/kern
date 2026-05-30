package com.kern.web

import com.kern.application.AppsService
import com.kern.web.dto.isInstalled
import com.kern.web.dto.labelRu
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/apps")
class AppsController(
    private val appsService: AppsService,
) {

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("apps", appsService.list())
        return "apps/index"
    }

    @GetMapping("/{slug}")
    fun detail(
        @PathVariable slug: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String {
        val app = appsService.get(slug) ?: run {
            redirectAttributes.addFlashAttribute("errorMessage", "Приложение не найдено: $slug")
            return "redirect:/apps"
        }
        val status = app.status()
        model.addAttribute("slug", slug)
        model.addAttribute("displayName", app.id.displayName)
        model.addAttribute("description", app.description)
        model.addAttribute("status", status)
        model.addAttribute("installLabel", status.installState.labelRu())
        model.addAttribute("serviceLabel", status.serviceState.labelRu())
        model.addAttribute("installed", status.installState.isInstalled())
        val configFields = app.loadConfig()
        model.addAttribute("configSections", configFields.groupBy { it.section })
        return "apps/detail"
    }

    @PostMapping("/{slug}/install")
    fun install(
        @PathVariable slug: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        val result = appsService.install(slug)
        redirectAttributes.addFlashAttribute(
            if (result.success) "successMessage" else "errorMessage",
            result.message,
        )
        return "redirect:/apps/$slug"
    }

    @PostMapping("/{slug}/config")
    fun saveConfig(
        @PathVariable slug: String,
        @RequestParam params: Map<String, String>,
        redirectAttributes: RedirectAttributes,
    ): String {
        val values = params.filterKeys { it.startsWith("cfg_") }
            .mapKeys { it.key.removePrefix("cfg_") }
        val result = appsService.saveConfig(slug, values)
        redirectAttributes.addFlashAttribute(
            if (result.success) "successMessage" else "errorMessage",
            result.message,
        )
        return "redirect:/apps/$slug"
    }
}
