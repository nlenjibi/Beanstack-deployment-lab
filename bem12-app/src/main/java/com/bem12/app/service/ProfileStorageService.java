package com.bem12.app.service;

import com.bem12.app.dto.ProfileUploadResponse;
import com.bem12.app.exception.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProfileStorageService.class);

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/gif",  "gif",
            "image/webp", "webp"
    );

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public ProfileStorageService(
            S3Client s3Client,
            @Value("${s3.bucket-name:bem12-profiles}") String bucketName,
            @Value("${s3.region:us-east-1}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    public ProfileUploadResponse upload(MultipartFile file) {
        validate(file);
        String key = buildKey(file.getContentType());
        putToS3(file, key);
        String url = buildUrl(key);
        log.info("Profile picture uploaded: key={}", key);
        return new ProfileUploadResponse(url, key, "Profile picture uploaded successfully");
    }

    // ── private helpers ─────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidFileException("File size must not exceed 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileException(
                    "File type not allowed. Accepted: JPEG, PNG, GIF, WebP");
        }
    }

    private String buildKey(String contentType) {
        String ext = EXTENSIONS.getOrDefault(contentType, "jpg");
        return "profiles/" + UUID.randomUUID() + "." + ext;
    }

    private void putToS3(MultipartFile file, String key) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (Exception e) {
            log.error("S3 upload failed for key={}: {}", key, e.getMessage());
            throw new RuntimeException("Upload failed. Please try again.");
        }
    }

    private String buildUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }
}
