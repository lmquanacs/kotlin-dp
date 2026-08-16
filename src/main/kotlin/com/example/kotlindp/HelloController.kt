package com.example.kotlindp

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/")
    fun hello(@RequestParam(defaultValue = "World") name: String): String = "Hello, $name!"

    @GetMapping("/hello")
    fun helloJson(@RequestParam(defaultValue = "World") name: String): Greeting =
        Greeting(message = "Hello, $name!")
}

data class Greeting(val message: String)
