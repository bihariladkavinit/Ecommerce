package com.ecomm.notification.sender;

/**
 * Strategy interface for delivering a notification.
 *
 * <p>Each implementation handles a specific channel (email, SMS, push, etc.).
 * The correct implementation is selected at runtime by
 * {@link NotificationSenderRouter} based on the
 * {@link com.ecomm.notification.entity.NotificationType} in the request.
 *
 * <p>Implementations must be Spring beans so they can be injected into the
 * router via the {@code Map<NotificationType, NotificationSender>} constructor.
 *
 * <h3>Extension point</h3>
 * To wire a real email provider (e.g. SendGrid, AWS SES):
 * <ol>
 *   <li>Create a new class implementing this interface.</li>
 *   <li>Annotate it with {@code @Component @Primary} (or use a profile).</li>
 *   <li>The router picks it up automatically — no other changes needed.</li>
 * </ol>
 */
public interface NotificationSender {

    /**
     * Sends the notification described by {@code request}.
     *
     * @param request the notification to deliver; never {@code null}
     * @throws NotificationSendException if delivery fails
     */
    void send(NotificationRequest request);
}
