package com.kern.web

import com.kern.application.FileManagerService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class FileManagerController(
    private val fileManagerService: FileManagerService,
) {

    @GetMapping("/files")
    fun files(model: Model): String {
        model.addAttribute(
            "data",
            mapOf(
                "host" to mapOf("hostname" to "…"),
                "defaultPath" to fileManagerService.defaultDirectory(),
            ),
        )
        return "files/index"
    }
}
