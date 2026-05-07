package com.bem12.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Map<String, AttributeValue>> fakeTable = new HashMap<>();

    @BeforeEach
    void setUp() {
        fakeTable.clear();
        userService = new UserService(dynamoDbClient, "bem12-users", passwordEncoder);
        stubDynamoDb();
    }

    @SuppressWarnings("unchecked")
    private void stubDynamoDb() {
        lenient().when(dynamoDbClient.getItem(any(Consumer.class))).thenAnswer(inv -> {
            Consumer<GetItemRequest.Builder> consumer = inv.getArgument(0);
            GetItemRequest.Builder b = GetItemRequest.builder();
            consumer.accept(b);
            GetItemRequest req = b.build();
            Map<String, AttributeValue> item = fakeTable.getOrDefault(
                    req.key().get("email").s(), Collections.emptyMap());
            return GetItemResponse.builder().item(item).build();
        });

        lenient().when(dynamoDbClient.putItem(any(Consumer.class))).thenAnswer(inv -> {
            Consumer<PutItemRequest.Builder> consumer = inv.getArgument(0);
            PutItemRequest.Builder b = PutItemRequest.builder();
            consumer.accept(b);
            PutItemRequest req = b.build();
            fakeTable.put(req.item().get("email").s(), new HashMap<>(req.item()));
            return PutItemResponse.builder().build();
        });

        lenient().when(dynamoDbClient.updateItem(any(Consumer.class))).thenAnswer(inv -> {
            Consumer<UpdateItemRequest.Builder> consumer = inv.getArgument(0);
            UpdateItemRequest.Builder b = UpdateItemRequest.builder();
            consumer.accept(b);
            UpdateItemRequest req = b.build();
            String email = req.key().get("email").s();
            String url = req.expressionAttributeValues().get(":url").s();
            fakeTable.computeIfAbsent(email, k -> new HashMap<>())
                     .put("profilePictureUrl", AttributeValue.fromS(url));
            return UpdateItemResponse.builder().build();
        });

        lenient().when(dynamoDbClient.scan(any(Consumer.class))).thenAnswer(inv ->
                ScanResponse.builder().count(fakeTable.size()).build()
        );
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
        userService.register("alice@example.com", "pass123");
        assertTrue(userService.getProfilePicture("alice@example.com").isEmpty());
    }

    @Test
    void test_updateProfilePicture_withValidUrl_storesUrl() {
        userService.register("alice@example.com", "pass123");
        String url = "https://bucket.s3.amazonaws.com/profiles/abc.jpg";
        userService.updateProfilePicture("alice@example.com", url);
        assertEquals(url, userService.getProfilePicture("alice@example.com").orElse(null));
    }

    @Test
    void test_updateProfilePicture_calledTwice_overwritesWithLatestUrl() {
        userService.register("alice@example.com", "pass123");
        userService.updateProfilePicture("alice@example.com", "https://first.jpg");
        userService.updateProfilePicture("alice@example.com", "https://second.jpg");
        assertEquals("https://second.jpg",
                userService.getProfilePicture("alice@example.com").orElse(null));
    }
}
