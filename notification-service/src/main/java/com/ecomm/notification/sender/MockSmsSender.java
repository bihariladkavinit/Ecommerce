package com.ecomm.notification.sender;

import com.ecomm.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock SMS sender — simulates successful SMS delivery by logging the
 * formatted message at INFO level.
 *
 * <p>No current event consumer triggers SMS notifications — this implementation
 * is wired and ready for use as soon as a consumer calls
 * {@link com.ecomm.notification.service.NotificationService} with
 * {@link NotificationType#SMS}.
 *
 * <h3>Log output format</h3>
 * <pre>
 * [SMS SENT] ───────────────────────────────────
 *   To   : +44 7700 900123
 *   Body : Your order has been shipped. Track: TRK-A1B2C3D4
 * ──────────────────────────────────────────────
 * </pre>
 *
 * @see NotificationSenderRouter
 */
@Component
@Slf4j
public class MockSmsSender implements NotificationSender {

    /** Identifies this bean in the router's type → sender map. */
    public static final NotificationType TYPE = NotificationType.SMS;

    @Override
    public void send(NotificationRequest request) {
        log.info("\n[SMS SENT] ───────────────────────────────────\n" +
                        "  To   : {}\n" +
                        "  Body : {}\n" +
                        "──────────────────────────────────────────────",
                request.getRecipient(),
                request.getBody());
    }
}
