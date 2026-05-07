package com.bem12.app.controller;

import com.bem12.app.dto.ProfileUploadResponse;
import com.bem12.app.service.ProfileStorageService;
import com.bem12.app.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileStorageService storageService;
    private final UserService userService;

    public ProfileController(ProfileStorageService storageService, UserService userService) {
        this.storageService = storageService;
        this.userService = userService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {
        ProfileUploadResponse response = storageService.upload(file);
        userService.updateProfilePicture(principal.getUsername(), response.url());
        log.info("Profile picture updated for user={}", principal.getUsername());
        return ResponseEntity.ok(response);
    }
}
