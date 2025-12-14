package com.forsaken.ecommerce.notification.repository;

import com.forsaken.ecommerce.notification.configs.dynamodb.DynamoDbProperties;
import com.forsaken.ecommerce.notification.models.Notification;
import com.forsaken.ecommerce.notification.models.NotificationType;
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

    public NotificationRepository(
            final DynamoDbEnhancedClient enhancedClient,
            final DynamoDbProperties dynamoDbProperties
    ) {
        this.client = enhancedClient;
        this.notificationTable = client.table(
                dynamoDbProperties.tableName(),
                TableSchema.fromBean(Notification.class)
        );
    }

    @Override
    public void save(final Notification notification) {
        Notification mutatedNotification = notification;
        if (null == notification.getId()) {
            mutatedNotification = Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .type(notification.getType())
                    .paymentConfirmation(notification.getPaymentConfirmation())
                    .orderConfirmation(notification.getOrderConfirmation())
                    .build();
        }
        notificationTable.putItem(mutatedNotification);
    }

    @Override
    public Notification findById(final String notificationId) {
        final Key key = Key.builder().partitionValue(notificationId).build();
        return notificationTable.getItem(r -> r.key(key));
    }

    @Override
    public List<Notification> findByType(final NotificationType notificationType) {
        final DynamoDbIndex<Notification> typeIndex = notificationTable.index("type-index");
        final List<Notification> results = new ArrayList<>();
        final SdkIterable<Page<Notification>> pages = typeIndex.query(r ->
                r.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(notificationType.name())))
        );
        for (final Page<Notification> page : pages) results.addAll(page.items());
        return results;
    }
}
