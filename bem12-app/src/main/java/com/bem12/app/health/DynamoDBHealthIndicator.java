package com.bem12.app.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;

@Component
public class DynamoDBHealthIndicator extends AbstractHealthIndicator {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDBHealthIndicator(DynamoDbClient client,
                                   @Value("${dynamodb.users-table-name:bem12-users}") String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            DescribeTableResponse response = client.describeTable(r -> r.tableName(tableName));
            String status = response.table().tableStatusAsString();
            builder.up()
                   .withDetail("table", tableName)
                   .withDetail("tableStatus", status);
        } catch (Exception e) {
            builder.down()
                   .withDetail("table", tableName)
                   .withDetail("error", e.getMessage());
        }
    }
}
