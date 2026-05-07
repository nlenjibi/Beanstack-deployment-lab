package com.bem12.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamoDBService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBService.class);

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDBService(DynamoDbClient client,
                           @Value("${dynamodb.table-name:bem12-visits}") String tableName) {
        this.client = client;
        this.tableName = tableName;
        log.info("DynamoDB service initialised — table={}", tableName);
    }

    public String getConnectionStatus() {
        try {
            client.describeTable(r -> r.tableName(tableName));
            return "CONNECTED";
        } catch (ResourceNotFoundException e) {
            log.warn("DynamoDB table '{}' not found", tableName);
            return "TABLE_NOT_FOUND";
        } catch (Exception e) {
            log.error("DynamoDB connection error: {}", e.getMessage());
            return "ERROR";
        }
    }

    public void recordVisit(String path) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("visitId", AttributeValue.fromS(UUID.randomUUID().toString()));
            item.put("path", AttributeValue.fromS(path));
            item.put("timestamp", AttributeValue.fromS(Instant.now().toString()));
            client.putItem(r -> r.tableName(tableName).item(item));
        } catch (Exception e) {
            log.warn("Could not record visit: {}", e.getMessage());
        }
    }

    public List<Map<String, String>> getRecentVisits() {
        try {
            ScanResponse result = client.scan(r -> r.tableName(tableName).limit(20));
            return result.items().stream()
                    .map(item -> {
                        Map<String, String> visit = new LinkedHashMap<>();
                        item.forEach((k, v) -> visit.put(k, v.s() != null ? v.s() : v.toString()));
                        return visit;
                    })
                    .sorted(Comparator.comparing(
                            v -> v.getOrDefault("timestamp", ""),
                            Comparator.reverseOrder()))
                    .limit(10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Could not fetch visits: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
