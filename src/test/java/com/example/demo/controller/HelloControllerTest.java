package com.example.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Hello from Spring Boot API!")))
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    void testHelloWithNameEndpoint() throws Exception {
        String name = "John";
        mockMvc.perform(get("/api/hello/{name}", name))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Hello " + name + " from Spring Boot API!")))
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    void testGreetEndpoint() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("name", "Alice");

        mockMvc.perform(post("/api/greet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.greeting", is("Greetings Alice!")))
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    void testGreetEndpointWithoutName() throws Exception {
        Map<String, String> request = new HashMap<>();

        mockMvc.perform(post("/api/greet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.greeting", is("Greetings World!")))
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("Spring Boot Demo API")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void testNonExistentEndpoint() throws Exception {
        mockMvc.perform(get("/api/notfound"))
                .andExpect(status().isNotFound());
    }
}