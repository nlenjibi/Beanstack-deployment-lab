package com.bem12.app.controller;

import com.bem12.app.config.PasswordEncoderConfig;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamoDBService dynamoDBService;

    @MockBean
    private UserService userService;

    @Test
    void health_returnsUpWithConnectedDb() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");

        mockMvc.perform(get("/health"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.service").value("BEM12 Application"))
               .andExpect(jsonPath("$.db_status").value("CONNECTED"));
    }

    @Test
    void health_whenDbError_stillReturnsUp() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("ERROR");

        mockMvc.perform(get("/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.db_status").value("ERROR"));
    }

    @Test
    void health_isAccessibleWithoutAuthentication() throws Exception {
        when(dynamoDBService.getConnectionStatus()).thenReturn("CONNECTED");

        mockMvc.perform(get("/health"))
               .andExpect(status().isOk());
    }
}
