package com.crm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1", ""})
public class HealthController {

    @GetMapping({"/health", "/health/", "/"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "CallingCRM Backend",
                "loginEndpoint", "/api/v1/auth/login",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
