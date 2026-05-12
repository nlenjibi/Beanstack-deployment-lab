package com.bem12.app.service;

import com.bem12.app.dto.ProfileUploadResponse;
import com.bem12.app.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileStorageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String REGION = "us-east-1";

    private S3Client mockS3;
    private ProfileStorageService service;

    @BeforeEach
    void setUp() {
        mockS3 = mock(S3Client.class);
        service = new ProfileStorageService(mockS3, BUCKET, REGION);
    }

    // ── happy paths ──────────────────────────────────────────────────────────

    @Test
    void test_upload_withValidJpeg_returnsProfileUploadResponse() {
        // Arrange
        doReturn(PutObjectResponse.builder().build())
                .when(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        MockMultipartFile file = jpeg("photo.jpg", 1024);

        // Act
        ProfileUploadResponse result = service.upload(file);

        // Assert
        assertNotNull(result.url());
        assertTrue(result.url().startsWith("https://test-bucket.s3.us-east-1.amazonaws.com/profiles/"));
        assertTrue(result.key().startsWith("profiles/"));
        assertTrue(result.key().endsWith(".jpg"));
        assertEquals("Profile picture uploaded successfully", result.message());
    }

    @Test
    void test_upload_withValidPng_returnsKeyWithPngExtension() {
        // Arrange
        doReturn(PutObjectResponse.builder().build())
                .when(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        MockMultipartFile file = png("photo.png", 512);

        // Act
        ProfileUploadResponse result = service.upload(file);

        // Assert
        assertTrue(result.key().endsWith(".png"));
    }

    @Test
    void test_upload_withValidFile_callsS3PutObject() {
        // Arrange
        doReturn(PutObjectResponse.builder().build())
                .when(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        MockMultipartFile file = jpeg("photo.jpg", 256);

        // Act
        service.upload(file);

        // Assert
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void test_upload_withValidFile_buildsCorrectS3Url() {
        // Arrange
        doReturn(PutObjectResponse.builder().build())
                .when(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        ProfileStorageService regionService =
                new ProfileStorageService(mockS3, "my-bucket", "eu-west-1");

        // Act
        ProfileUploadResponse result = regionService.upload(jpeg("p.jpg", 100));

        // Assert
        assertTrue(result.url().contains("my-bucket.s3.eu-west-1.amazonaws.com"));
    }

    // ── validation: empty / size / type ──────────────────────────────────────

    @Test
    void test_upload_withEmptyFile_throwsInvalidFileException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        // Act + Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> service.upload(file));
        assertEquals("File must not be empty", ex.getMessage());
        verifyNoInteractions(mockS3);
    }

    @Test
    void test_upload_withFileLargerThan5MB_throwsInvalidFileException() {
        // Arrange — 5 MB + 1 byte
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", oversized);

        // Act + Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> service.upload(file));
        assertTrue(ex.getMessage().contains("5 MB"));
        verifyNoInteractions(mockS3);
    }

    @Test
    void test_upload_withDisallowedContentType_throwsInvalidFileException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        // Act + Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> service.upload(file));
        assertTrue(ex.getMessage().contains("not allowed"));
        verifyNoInteractions(mockS3);
    }

    @Test
    void test_upload_withNullContentType_throwsInvalidFileException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", null, new byte[100]);

        // Act + Assert
        assertThrows(InvalidFileException.class, () -> service.upload(file));
        verifyNoInteractions(mockS3);
    }

    // ── error path ────────────────────────────────────────────────────────────

    @Test
    void test_upload_whenS3Throws_throwsRuntimeException() {
        // Arrange
        doThrow(new RuntimeException("S3 unavailable"))
                .when(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        MockMultipartFile file = jpeg("photo.jpg", 256);

        // Act + Assert
        assertThrows(RuntimeException.class, () -> service.upload(file));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockMultipartFile jpeg(String name, int sizeBytes) {
        return new MockMultipartFile("file", name, "image/jpeg", new byte[sizeBytes]);
    }

    private MockMultipartFile png(String name, int sizeBytes) {
        return new MockMultipartFile("file", name, "image/png", new byte[sizeBytes]);
    }
}
