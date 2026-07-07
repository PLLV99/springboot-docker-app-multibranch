package com.example.demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    // เพิ่ม endpoint ใหม่สำหรับทดสอบ
    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> products() {
        List<Map<String, Object>> products = new ArrayList<>();

        Map<String, Object> product1 = new HashMap<>();
        product1.put("id", 1);
        product1.put("name", "Laptop");
        product1.put("price", 25000.00);
        products.add(product1);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("id", 2);
        product2.put("name", "Mouse");
        product2.put("price", 500.00);
        products.add(product2);

        Map<String, Object> product3 = new HashMap<>();
        product3.put("id", 3);
        product3.put("name", "Keyboard");
        product3.put("price", 1200.00);
        products.add(product3);

        return ResponseEntity.ok(products);
    }
}