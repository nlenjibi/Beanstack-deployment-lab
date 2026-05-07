package com.bem12.app.controller;

import com.bem12.app.service.DynamoDBService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BEM12Controller {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private final DynamoDBService dynamoDBService;
    private final MeterRegistry meterRegistry;

    public BEM12Controller(DynamoDBService dynamoDBService, MeterRegistry meterRegistry) {
        this.dynamoDBService = dynamoDBService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        dynamoDBService.recordVisit("/api/status");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("message", "Elastic Beanstalk Deployment Successful!");
        body.put("version", appVersion);
        body.put("timestamp", Instant.now().toString());
        body.put("external_service", "DynamoDB");
        body.put("db_status", dynamoDBService.getConnectionStatus());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("application", "BEM12 Lab");
        body.put("deployment", "Elastic Beanstalk");
        body.put("ci_cd", "GitHub Actions");
        body.put("external_service", "DynamoDB");
        body.put("version", appVersion);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> data() {
        dynamoDBService.recordVisit("/api/data");
        List<Map<String, String>> visits = dynamoDBService.getRecentVisits();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "DynamoDB");
        body.put("table_status", dynamoDBService.getConnectionStatus());
        body.put("count", visits.size());
        body.put("recent_visits", visits);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());

        double heapUsed = meterRegistry.get("jvm.memory.used")
                .tag("area", "heap").gauge().value();
        double heapMax = meterRegistry.get("jvm.memory.max")
                .tag("area", "heap").gauge().value();

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heap_used_mb",  Math.round(heapUsed  / 1024 / 1024));
        memory.put("heap_max_mb",   Math.round(heapMax   / 1024 / 1024));
        memory.put("heap_used_pct", heapMax > 0
                ? Math.round(heapUsed / heapMax * 100) + "%" : "n/a");
        body.put("jvm_memory", memory);

        double uptimeMs = meterRegistry.get("process.uptime").timeGauge().value(
                java.util.concurrent.TimeUnit.MILLISECONDS);
        body.put("uptime_seconds", Math.round(uptimeMs / 1000));

        body.put("db_status", dynamoDBService.getConnectionStatus());
        return ResponseEntity.ok(body);
    }
}
