package com.bem12.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.Map;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_PASSWORD_HASH = "passwordHash";
    private static final String ATTR_PROFILE_URL = "profilePictureUrl";

    private final DynamoDbClient client;
    private final String tableName;
    private final PasswordEncoder passwordEncoder;

    public UserService(DynamoDbClient client,
                       @Value("${dynamodb.users-table-name:bem12-users}") String tableName,
                       PasswordEncoder passwordEncoder) {
        this.client = client;
        this.tableName = tableName;
        this.passwordEncoder = passwordEncoder;
        log.info("UserService initialised — table={}", tableName);
    }

    public boolean register(String email, String rawPassword) {
        if (userExists(email)) return false;
        Map<String, AttributeValue> item = Map.of(
                ATTR_EMAIL, AttributeValue.fromS(email),
                ATTR_PASSWORD_HASH, AttributeValue.fromS(passwordEncoder.encode(rawPassword))
        );
        client.putItem(r -> r.tableName(tableName).item(item));
        log.info("Registered user: {}", email);
        return true;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Map<String, AttributeValue> item = fetchItem(email);
        if (item.isEmpty()) throw new UsernameNotFoundException("User not found: " + email);
        return User.builder()
                .username(email)
                .password(item.get(ATTR_PASSWORD_HASH).s())
                .roles("USER")
                .build();
    }

    public void updateProfilePicture(String email, String url) {
        client.updateItem(r -> r
                .tableName(tableName)
                .key(Map.of(ATTR_EMAIL, AttributeValue.fromS(email)))
                .updateExpression("SET profilePictureUrl = :url")
                .expressionAttributeValues(Map.of(":url", AttributeValue.fromS(url)))
        );
    }

    public Optional<String> getProfilePicture(String email) {
        Map<String, AttributeValue> item = fetchItem(email);
        if (!item.containsKey(ATTR_PROFILE_URL)) return Optional.empty();
        return Optional.ofNullable(item.get(ATTR_PROFILE_URL).s());
    }

    public int getUserCount() {
        Integer count = client.scan(r -> r.tableName(tableName).select(Select.COUNT)).count();
        return count != null ? count : 0;
    }

    private boolean userExists(String email) {
        return !fetchItem(email).isEmpty();
    }

    private Map<String, AttributeValue> fetchItem(String email) {
        GetItemResponse response = client.getItem(r -> r
                .tableName(tableName)
                .key(Map.of(ATTR_EMAIL, AttributeValue.fromS(email)))
        );
        Map<String, AttributeValue> item = response.item();
        return item != null ? item : Map.of();
    }
}
