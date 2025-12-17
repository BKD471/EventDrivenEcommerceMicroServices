package com.forsaken.ecommerce.notification.models;

/**
 * Represents the different types of events that can trigger notifications
 * within the e-commerce application.
 */
public enum EventType {
    /**
     * An event related to a payment action, such as creation, update,
     * or completion of a payment.
     */
    PAYMENT,
    /**
     * An event related to an order action, such as creation, update,
     * or fulfillment of an order.
     */
    ORDER
}
