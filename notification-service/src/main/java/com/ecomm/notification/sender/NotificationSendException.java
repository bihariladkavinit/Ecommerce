package com.ecomm.notification.sender;

/**
 * Signals that a notification send attempt failed.
 *
 * <p>Thrown by {@link NotificationSender} implementations when delivery
 * cannot be completed. The {@link com.ecomm.notification.service.NotificationService}
 * catches this and writes a {@code FAILED} status to
 * {@link com.ecomm.notification.entity.NotificationLog}.
 *
 * <p>In mock implementations this exception is never thrown (all simulated
 * sends succeed), but the service layer is coded defensively with a broader
 * {@code catch (Exception)} so any unexpected runtime error also records FAILED.
 */
public class NotificationSendException extends RuntimeException {

    public NotificationSendException(String message) {
        super(message);
    }

    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
