package com.ecomm.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Notification Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Consumes {@code user.registered}, {@code order.confirmed},
 *       {@code order.cancelled}, and {@code shipment.create.reply} from Kafka.</li>
 *   <li>Delegates to mock sender implementations (email/SMS).</li>
 *   <li>Logs every send attempt (SENT or FAILED) to {@code notification_log}.</li>
 *   <li>Exposes an optional admin endpoint for notification history lookup.</li>
 * </ul>
 *
 * <p>This service is a <strong>pure Kafka consumer</strong> — it never
 * publishes events and has no transactional outbox. {@code @EnableScheduling}
 * is therefore not needed.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
