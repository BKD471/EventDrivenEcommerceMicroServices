package com.forsaken.ecommerce.notification.repository;

import com.forsaken.ecommerce.notification.models.Notification;
import com.forsaken.ecommerce.notification.models.NotificationType;

import java.util.List;

/**
 * Repository abstraction for managing {@link Notification} entities.
 * <p>
 * This interface defines the persistence contract for notifications and
 * represents the boundary between the domain/service layer and the
 * underlying data store implementation.
 * </p>
 *
 * <p>
 * The repository follows the <b>Repository Pattern</b>, providing a
 * collection-like interface for accessing and persisting notification
 * entities while hiding storage-specific details.
 * </p>
 *
 * <p><b>Design Principles:</b></p>
 * <ul>
 *     <li>Technology-agnostic contract to allow multiple persistence
 *         implementations (e.g., DynamoDB, RDBMS, NoSQL).</li>
 *     <li>Supports clean separation of concerns between domain logic
 *         and data access.</li>
 *     <li>All implementations must be thread-safe.</li>
 * </ul>
 *
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *     <li>Current production implementation is backed by Amazon DynamoDB.</li>
 *     <li>Queries by notification type may leverage secondary indexes
 *         for efficient lookups.</li>
 * </ul>
 *
 * <p><b>Spring Integration:</b></p>
 * <ul>
 *     <li>This interface itself is not responsible for Spring bean creation.</li>
 *     <li>Concrete implementations must be annotated with
 *         {@link org.springframework.stereotype.Repository}.</li>
 *     <li>Consumers should depend on this interface rather than concrete
 *         implementations to enable loose coupling and easier testing.</li>
 * </ul>
 */
public interface INotificationRepository {

    /**
     * Persists the given notification entity.
     * <p>
     * Implementations may generate a unique identifier for the notification
     * if one is not already present.
     * </p>
     *
     * @param notification the notification to persist; must not be {@code null}
     * @throws IllegalArgumentException if {@code notification} is {@code null}
     */
    void save(final Notification notification);

    /**
     * Retrieves a notification by its unique identifier.
     *
     * @param notificationId the notification identifier; must not be {@code null}
     * @return the matching {@link Notification}, or {@code null} if no
     * notification exists with the given identifier
     * @throws IllegalArgumentException if {@code id} is {@code null}
     */
    Notification findById(final String notificationId);

    /**
     * Retrieves all notifications matching the given {@link NotificationType}.
     * <p>
     * Implementations are expected to perform this operation efficiently,
     * potentially using secondary indexes or equivalent mechanisms
     * provided by the underlying data store.
     * </p>
     *
     * @param notificationType the notification type; must not be {@code null}
     * @return a list of matching {@link Notification} entities;
     * never {@code null}, but may be empty if no matches are found
     * @throws IllegalArgumentException if {@code notificationType} is {@code null}
     */
    List<Notification> findByType(final NotificationType notificationType);
}
