package com.forsaken.ecommerce.notification.repository;

import com.forsaken.ecommerce.notification.models.Notification;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository abstraction for managing {@link Notification} entities.
 * <p>
 * This interface defines persistence operations for notifications and
 * serves as a contract between the domain/service layer and the underlying
 * data store implementation.
 * </p>
 *
 * <p><b>Design Notes:</b></p>
 * <ul>
 *     <li>Implementation is backed by Amazon DynamoDB.</li>
 *     <li>The interface remains technology-agnostic to allow future
 *         replacement or extension (e.g., RDBMS, MongoDB).</li>
 *     <li>All implementations are expected to be thread-safe.</li>
 * </ul>
 *
 * <p><b>Spring Integration:</b></p>
 * <ul>
 *     <li>This interface itself is not responsible for Spring bean creation.</li>
 *     <li>The concrete implementation must be annotated with
 *         {@link Repository}.</li>
 * </ul>
 */
public interface INotificationRepository {

    /**
     * Persists a notification entity.
     * <p>
     * If the notification does not already contain an identifier,
     * the implementation may generate one before persisting.
     * </p>
     *
     * @param notification the notification to persist
     * @throws IllegalArgumentException if the notification is {@code null}
     */
    void save(final Notification notification);

    /**
     * Retrieves a notification by its unique identifier.
     *
     * @param id the notification identifier
     * @return the matching {@link Notification}, or {@code null} if not found
     * @throws IllegalArgumentException if {@code id} is {@code null}
     */
    Notification findById(final String id);

    /**
     * Retrieves all notifications matching the given type.
     * <p>
     * Implementations may leverage secondary indexes to efficiently
     * perform this query.
     * </p>
     *
     * @param type the notification type
     * @return a list of matching notifications, or an empty list if none exist
     * @throws IllegalArgumentException if {@code type} is {@code null}
     */
    List<Notification> findByType(final String type);
}
