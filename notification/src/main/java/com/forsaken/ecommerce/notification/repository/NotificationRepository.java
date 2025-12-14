package com.forsaken.ecommerce.notification.repository;

import com.forsaken.ecommerce.notification.configs.dynamodb.DynamoDbProperties;
import com.forsaken.ecommerce.notification.models.Notification;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationRepository implements INotificationRepository {

    private final DynamoDbTable<Notification> notificationTable;
    private final DynamoDbEnhancedClient client;
    private final DynamoDbProperties dynamoDbProperties;

    public NotificationRepository(
            final DynamoDbEnhancedClient enhancedClient,
            final DynamoDbProperties dynamoDbProperties
    ) {
        this.dynamoDbProperties = dynamoDbProperties;
        this.client = enhancedClient;
        this.notificationTable = client.table(
                dynamoDbProperties.tableName(),
                TableSchema.fromBean(Notification.class)
        );
    }

    @Override
    public void save(final Notification notification) {
        if (notification.getId() == null) notification.setId(UUID.randomUUID().toString());
        notificationTable.putItem(notification);
    }

    @Override
    public Notification findById(final String id) {
        final Key key = Key.builder().partitionValue(id).build();
        return notificationTable.getItem(r -> r.key(key));
    }

    @Override
    public List<Notification> findByType(final String type) {
        final DynamoDbIndex<Notification> typeIndex = notificationTable.index("type-index");
        final List<Notification> results = new ArrayList<>();
        final SdkIterable<Page<Notification>> pages = typeIndex.query(r ->
                r.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(type)))
        );
        for (final Page<Notification> page : pages) results.addAll(page.items());
        return results;
    }
}
