package com.example.kotlindp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `root returns plain greeting`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string("Hello, World!"))
    }

    @Test
    fun `hello returns json greeting with name`() {
        mockMvc.perform(get("/hello").param("name", "Kotlin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Hello, Kotlin!"))
    }
}
