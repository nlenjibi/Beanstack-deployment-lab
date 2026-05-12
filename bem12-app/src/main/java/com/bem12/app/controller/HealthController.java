package com.bem12.app.controller;

import com.bem12.app.service.DynamoDBService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private final DynamoDBService dynamoDBService;

    public HealthController(DynamoDBService dynamoDBService) {
        this.dynamoDBService = dynamoDBService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String dbStatus = dynamoDBService.getConnectionStatus();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "BEM12 Application");
        body.put("version", appVersion);
        body.put("db_status", dbStatus);
        return ResponseEntity.ok(body);
    }
}
