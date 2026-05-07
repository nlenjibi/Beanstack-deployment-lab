package com.bem12.app.controller;

import com.bem12.app.dto.ProfileUploadResponse;
import com.bem12.app.exception.InvalidFileException;
import com.bem12.app.security.SecurityConfig;
import com.bem12.app.service.ProfileStorageService;
import com.bem12.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileStorageService storageService;

    @MockBean
    private UserService userService;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com", roles = "USER")
    void test_upload_withValidJpegAndAuthenticatedUser_returnsOk() throws Exception {
        // Arrange
        ProfileUploadResponse response = new ProfileUploadResponse(
                "https://bucket.s3.amazonaws.com/profiles/abc.jpg",
                "profiles/abc.jpg",
                "Profile picture uploaded successfully"
        );
        when(storageService.upload(any())).thenReturn(response);
        MockMultipartFile file = jpeg("photo.jpg");

        // Act + Assert
        mockMvc.perform(multipart("/api/profile/upload")
                        .file(file)
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.url").value("https://bucket.s3.amazonaws.com/profiles/abc.jpg"))
               .andExpect(jsonPath("$.message").value("Profile picture uploaded successfully"));

        verify(userService, times(1))
                .updateProfilePicture(eq("alice@example.com"), any());
    }

    // ── security: unauthenticated ─────────────────────────────────────────────

    @Test
    void test_upload_withoutAuthentication_returnsUnauthorized() throws Exception {
        // Arrange
        MockMultipartFile file = jpeg("photo.jpg");

        // Act + Assert
        mockMvc.perform(multipart("/api/profile/upload")
                        .file(file)
                        .with(csrf()))
               .andExpect(status().is3xxRedirection()); // redirects to /login
    }

    // ── validation errors ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com", roles = "USER")
    void test_upload_withInvalidFileType_returnsBadRequest() throws Exception {
        // Arrange
        when(storageService.upload(any()))
                .thenThrow(new InvalidFileException("File type not allowed"));
        MockMultipartFile file = pdf("document.pdf");

        // Act + Assert
        mockMvc.perform(multipart("/api/profile/upload")
                        .file(file)
                        .with(csrf()))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("File type not allowed"));
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = "USER")
    void test_upload_withEmptyFile_returnsBadRequest() throws Exception {
        // Arrange
        when(storageService.upload(any()))
                .thenThrow(new InvalidFileException("File must not be empty"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        // Act + Assert
        mockMvc.perform(multipart("/api/profile/upload")
                        .file(file)
                        .with(csrf()))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("File must not be empty"));
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = "USER")
    void test_upload_whenStorageServiceThrows_returnsInternalServerError() throws Exception {
        // Arrange
        when(storageService.upload(any()))
                .thenThrow(new RuntimeException("S3 connection failed"));
        MockMultipartFile file = jpeg("photo.jpg");

        // Act + Assert
        mockMvc.perform(multipart("/api/profile/upload")
                        .file(file)
                        .with(csrf()))
               .andExpect(status().isInternalServerError())
               .andExpect(jsonPath("$.error").value("An unexpected error occurred"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", new byte[512]);
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[512]);
    }
}
