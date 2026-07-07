package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello from Spring Boot API!");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/hello/{name}")
    public ResponseEntity<Map<String, String>> helloWithName(@PathVariable String name) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello " + name + " from Spring Boot API!");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/greet")
    public ResponseEntity<Map<String, String>> greet(@RequestBody Map<String, String> request) {
        String name = request.getOrDefault("name", "World");
        Map<String, String> response = new HashMap<>();
        response.put("greeting", "Greetings " + name + "!");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "Spring Boot Demo API");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> response = new HashMap<>();
        response.put("app", "Spring Boot Demo Application");
        response.put("version", "1.0.0");
        response.put("description", "A simple Spring Boot application with Docker support.");
        return ResponseEntity.ok(response);
    }
}