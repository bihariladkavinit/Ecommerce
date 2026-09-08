package com.ecomm.notification.entity;

/**
 * Channel over which a notification is delivered.
 *
 * <p>Only {@code EMAIL} is triggered by current event consumers.
 * {@code SMS} is reserved for future use — the sender abstraction
 * and router already support it.
 */
public enum NotificationType {
    EMAIL,
    SMS
}
