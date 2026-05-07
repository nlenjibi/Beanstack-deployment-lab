package com.bem12.app.controller;

import com.bem12.app.config.PasswordEncoderConfig;
import com.bem12.app.security.SecurityConfig;
import com.bem12.app.service.DynamoDBService;
import com.bem12.app.service.UserService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.search.RequiredSearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BEM12Controller.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
class BEM12ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamoDBService dynamoDBService;

    @MockBean
    private UserService userService;

    @MockBean
    private MeterRegistry meterRegistry;

    @Test
    void status_returnsSuccessWithDbStatus() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");

        mockMvc.perform(get("/api/status"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.status").value("success"))
               .andExpect(jsonPath("$.message").value("Elastic Beanstalk Deployment Successful!"))
               .andExpect(jsonPath("$.db_status").value("CONNECTED"))
               .andExpect(jsonPath("$.version").exists())
               .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void info_returnsApplicationMetadata() throws Exception {
        mockMvc.perform(get("/api/info"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.application").value("BEM12 Lab"))
               .andExpect(jsonPath("$.deployment").value("Elastic Beanstalk"))
               .andExpect(jsonPath("$.ci_cd").value("GitHub Actions"))
               .andExpect(jsonPath("$.external_service").value("DynamoDB"));
    }

    @Test
    void data_returnsVisitLogFromDynamoDB() throws Exception {
        Map<String, String> visit = Map.of(
                "visitId", "abc-123",
                "path", "/api/status",
                "timestamp", "2026-01-01T00:00:00Z"
        );
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");
        when(dynamoDBService.getRecentVisits()).thenReturn(List.of(visit));

        mockMvc.perform(get("/api/data"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.source").value("DynamoDB"))
               .andExpect(jsonPath("$.count").value(1))
               .andExpect(jsonPath("$.recent_visits[0].path").value("/api/status"));
    }

    @Test
    void data_whenNoVisits_returnsEmptyList() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");
        when(dynamoDBService.getRecentVisits()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/data"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.count").value(0))
               .andExpect(jsonPath("$.recent_visits").isArray());
    }

    @Test
    void metrics_returnsJvmAndDbData() throws Exception {
        Gauge heapUsed = mock(Gauge.class);
        Gauge heapMax  = mock(Gauge.class);
        TimeGauge uptime = mock(TimeGauge.class);

        when(heapUsed.value()).thenReturn(128_000_000.0);
        when(heapMax.value()).thenReturn(256_000_000.0);
        when(uptime.value(TimeUnit.MILLISECONDS)).thenReturn(60_000.0);
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");

        RequiredSearch heapUsedSearch = mock(RequiredSearch.class);
        RequiredSearch heapMaxSearch  = mock(RequiredSearch.class);
        RequiredSearch uptimeSearch   = mock(RequiredSearch.class);

        when(meterRegistry.get("jvm.memory.used")).thenReturn(heapUsedSearch);
        when(meterRegistry.get("jvm.memory.max")).thenReturn(heapMaxSearch);
        when(meterRegistry.get("process.uptime")).thenReturn(uptimeSearch);

        when(heapUsedSearch.tag(anyString(), anyString())).thenReturn(heapUsedSearch);
        when(heapMaxSearch.tag(anyString(), anyString())).thenReturn(heapMaxSearch);
        when(heapUsedSearch.gauge()).thenReturn(heapUsed);
        when(heapMaxSearch.gauge()).thenReturn(heapMax);
        when(uptimeSearch.timeGauge()).thenReturn(uptime);

        mockMvc.perform(get("/api/metrics"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.jvm_memory.heap_used_mb").exists())
               .andExpect(jsonPath("$.jvm_memory.heap_max_mb").exists())
               .andExpect(jsonPath("$.jvm_memory.heap_used_pct").exists())
               .andExpect(jsonPath("$.uptime_seconds").exists())
               .andExpect(jsonPath("$.db_status").value("CONNECTED"));
    }

    @Test
    void apiEndpoints_areAccessibleWithoutAuthentication() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");
        when(dynamoDBService.getRecentVisits()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/info")).andExpect(status().isOk());
        mockMvc.perform(get("/api/data")).andExpect(status().isOk());
    }
}
