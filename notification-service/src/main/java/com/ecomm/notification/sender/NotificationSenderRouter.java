package com.ecomm.notification.sender;

import com.ecomm.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Routes a {@link NotificationType} to the correct {@link NotificationSender}
 * implementation.
 *
 * <p>The type → sender map is built at construction time from the two mock
 * senders ({@link MockEmailSender}, {@link MockSmsSender}). This keeps
 * {@link com.ecomm.notification.service.NotificationService} fully decoupled
 * from concrete implementations — adding a new channel means adding a new
 * {@code NotificationSender} bean and registering it here.
 *
 * <p>If an unknown {@link NotificationType} is passed (e.g. a future PUSH type
 * not yet registered), {@link #route} throws {@link NotificationSendException}
 * so the service records a FAILED log entry rather than silently dropping the
 * notification.
 */
@Component
@Slf4j
public class NotificationSenderRouter {

    private final Map<NotificationType, NotificationSender> senders;

    public NotificationSenderRouter(MockEmailSender emailSender,
                                    MockSmsSender   smsSender) {
        this.senders = Map.of(
                NotificationType.EMAIL, emailSender,
                NotificationType.SMS,   smsSender
        );
    }

    /**
     * Returns the sender registered for the given {@code type}.
     *
     * @param type the notification channel
     * @return the matching {@link NotificationSender}
     * @throws NotificationSendException if no sender is registered for the type
     */
    public NotificationSender route(NotificationType type) {
        NotificationSender sender = senders.get(type);
        if (sender == null) {
            throw new NotificationSendException(
                    "No sender registered for notification type: " + type);
        }
        return sender;
    }
}
