package com.ecomm.notification.entity;

/**
 * Outcome of a single notification send attempt.
 *
 * <ul>
 *   <li>{@code SENT}   — the mock sender completed without throwing.</li>
 *   <li>{@code FAILED} — the mock sender threw an exception; the error
 *       message is captured in the log for debugging.</li>
 * </ul>
 */
public enum NotificationStatus {
    SENT,
    FAILED
}
