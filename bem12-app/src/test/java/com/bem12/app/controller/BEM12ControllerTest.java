package com.bem12.app.controller;

import com.bem12.app.security.SecurityConfig;
import com.bem12.app.service.DynamoDBService;
import com.bem12.app.service.UserService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BEM12Controller.class)
@Import(SecurityConfig.class)
class BEM12ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamoDBService dynamoDBService;

    @MockBean
    private UserService userService;

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
    void apiEndpoints_areAccessibleWithoutAuthentication() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");
        when(dynamoDBService.getRecentVisits()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/info")).andExpect(status().isOk());
        mockMvc.perform(get("/api/data")).andExpect(status().isOk());
    }
}
