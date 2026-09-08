package com.ecomm.notification.sender;

import com.ecomm.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock email sender — simulates successful email delivery by logging the
 * formatted message at INFO level.
 *
 * <p>No external dependency is required; the output is visible in the
 * application log and easy to verify during local development.
 *
 * <h3>Log output format</h3>
 * <pre>
 * [EMAIL SENT] ─────────────────────────────────
 *   To      : user@example.com
 *   Subject : Welcome to our store!
 *   Body    : Hi John, your account has been created successfully.
 * ──────────────────────────────────────────────
 * </pre>
 *
 * <p>In this mock all sends succeed. The
 * {@link com.ecomm.notification.service.NotificationService} wraps the call
 * in a try/catch so any unexpected runtime error will still produce a FAILED
 * log entry.
 *
 * @see NotificationSenderRouter
 */
@Component
@Slf4j
public class MockEmailSender implements NotificationSender {

    /** Identifies this bean in the router's type → sender map. */
    public static final NotificationType TYPE = NotificationType.EMAIL;

    @Override
    public void send(NotificationRequest request) {
        log.info("\n[EMAIL SENT] ─────────────────────────────────\n" +
                        "  To      : {}\n" +
                        "  Subject : {}\n" +
                        "  Body    : {}\n" +
                        "──────────────────────────────────────────────",
                request.getRecipient(),
                request.getSubject(),
                request.getBody());
    }
}
