package com.example.demo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
class HelloControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHelloEndpointIntegration() {
        String url = "http://localhost:" + port + "/api/hello";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("message")).isEqualTo("Hello from Spring Boot API!");
        assertThat(response.getBody().get("status")).isEqualTo("success");
    }

    @Test
    void testHealthEndpointIntegration() {
        String url = "http://localhost:" + port + "/api/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void testGreetEndpointIntegration() {
        String url = "http://localhost:" + port + "/api/greet";
        
        Map<String, String> request = new HashMap<>();
        request.put("name", "Integration Test");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("greeting");
        assertThat(response.getBody().get("greeting")).isEqualTo("Greetings Integration Test!");
        assertThat(response.getBody().get("status")).isEqualTo("success");
    }
}