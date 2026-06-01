package com.kern.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SecurityController {

    @GetMapping("/security")
    fun security(model: Model): String {
        model.addAttribute(
            "data",
            mapOf("host" to mapOf("hostname" to "…")),
        )
        return "security/index"
    }
}
