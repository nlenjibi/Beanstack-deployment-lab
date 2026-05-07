package com.bem12.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(new BCryptPasswordEncoder());
    }

    @Test
    void register_newUser_returnsTrue() {
        assertTrue(userService.register("alice@example.com", "password123"));
    }

    @Test
    void register_duplicateEmail_returnsFalse() {
        userService.register("alice@example.com", "password123");
        assertFalse(userService.register("alice@example.com", "different"));
    }

    @Test
    void register_passwordIsHashed() {
        userService.register("alice@example.com", "plaintext");
        UserDetails user = userService.loadUserByUsername("alice@example.com");
        assertNotEquals("plaintext", user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$"));
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        userService.register("alice@example.com", "password123");
        UserDetails user = userService.loadUserByUsername("alice@example.com");
        assertEquals("alice@example.com", user.getUsername());
    }

    @Test
    void loadUserByUsername_unknownUser_throwsException() {
        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("ghost@example.com"));
    }

    @Test
    void register_userHasUserRole() {
        userService.register("alice@example.com", "password123");
        UserDetails user = userService.loadUserByUsername("alice@example.com");
        assertTrue(user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void getUserCount_reflectsRegisteredUsers() {
        assertEquals(0, userService.getUserCount());
        userService.register("a@example.com", "pass");
        userService.register("b@example.com", "pass");
        assertEquals(2, userService.getUserCount());
    }

    // ── profile picture ───────────────────────────────────────────────────────

    @Test
    void test_getProfilePicture_withNoUpload_returnsEmpty() {
        // Arrange
        userService.register("alice@example.com", "pass123");

        // Act + Assert
        assertTrue(userService.getProfilePicture("alice@example.com").isEmpty());
    }

    @Test
    void test_updateProfilePicture_withValidUrl_storesUrl() {
        // Arrange
        userService.register("alice@example.com", "pass123");
        String url = "https://bucket.s3.amazonaws.com/profiles/abc.jpg";

        // Act
        userService.updateProfilePicture("alice@example.com", url);

        // Assert
        assertEquals(url, userService.getProfilePicture("alice@example.com").orElse(null));
    }

    @Test
    void test_updateProfilePicture_calledTwice_overwritesWithLatestUrl() {
        // Arrange
        userService.register("alice@example.com", "pass123");

        // Act
        userService.updateProfilePicture("alice@example.com", "https://first.jpg");
        userService.updateProfilePicture("alice@example.com", "https://second.jpg");

        // Assert
        assertEquals("https://second.jpg",
                userService.getProfilePicture("alice@example.com").orElse(null));
    }
}
