package com.kern

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class KernApplicationSmokeTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `application context loads and main pages respond`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Мониторинг")))

        mockMvc.perform(get("/apps"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Приложения")))

        mockMvc.perform(get("/apps/nginx"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Nginx")))

        mockMvc.perform(get("/api/v1/monitoring"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("host")))

        mockMvc.perform(get("/api/v1/apps"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("nginx")))
    }
}
