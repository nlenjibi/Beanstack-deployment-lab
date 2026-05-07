package com.bem12.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamoDBServiceTest {

    private DynamoDbClient mockClient;
    private DynamoDBService service;

    @BeforeEach
    void setUp() {
        mockClient = mock(DynamoDbClient.class);
        service = new DynamoDBService(mockClient, "test-visits");
    }

    @Test
    void getConnectionStatus_tableExists_returnsConnected() {
        doReturn(DescribeTableResponse.builder().build())
                .when(mockClient).describeTable(any(DescribeTableRequest.class));

        assertEquals("CONNECTED", service.getConnectionStatus());
    }

    @Test
    void getConnectionStatus_tableNotFound_returnsTableNotFound() {
        doThrow(ResourceNotFoundException.builder().message("Not found").build())
                .when(mockClient).describeTable(any(DescribeTableRequest.class));

        assertEquals("TABLE_NOT_FOUND", service.getConnectionStatus());
    }

    @Test
    void getConnectionStatus_networkError_returnsError() {
        doThrow(new RuntimeException("network error"))
                .when(mockClient).describeTable(any(DescribeTableRequest.class));

        assertEquals("ERROR", service.getConnectionStatus());
    }

    @Test
    void recordVisit_callsPutItem() {
        doReturn(PutItemResponse.builder().build())
                .when(mockClient).putItem(any(PutItemRequest.class));

        assertDoesNotThrow(() -> service.recordVisit("/test"));
        verify(mockClient, times(1)).putItem(any(PutItemRequest.class));
    }

    @Test
    void recordVisit_whenPutItemFails_doesNotThrow() {
        doThrow(new RuntimeException("write failed"))
                .when(mockClient).putItem(any(PutItemRequest.class));

        assertDoesNotThrow(() -> service.recordVisit("/test"));
    }

    @Test
    void getRecentVisits_returnsMappedItems() {
        Map<String, AttributeValue> item = Map.of(
                "visitId", AttributeValue.fromS("abc-123"),
                "path", AttributeValue.fromS("/api/status"),
                "timestamp", AttributeValue.fromS("2026-01-01T00:00:00Z")
        );
        doReturn(ScanResponse.builder().items(item).build())
                .when(mockClient).scan(any(ScanRequest.class));

        List<Map<String, String>> visits = service.getRecentVisits();
        assertEquals(1, visits.size());
        assertEquals("/api/status", visits.get(0).get("path"));
    }

    @Test
    void getRecentVisits_whenScanFails_returnsEmptyList() {
        doThrow(new RuntimeException("scan failed"))
                .when(mockClient).scan(any(ScanRequest.class));

        List<Map<String, String>> result = service.getRecentVisits();
        assertTrue(result.isEmpty());
    }
}
