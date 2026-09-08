package com.ecomm.notification.service;

import com.ecomm.notification.entity.*;
import com.ecomm.notification.repository.NotificationLogRepository;
import com.ecomm.notification.repository.ProcessedEventRepository;
import com.ecomm.notification.sender.NotificationRequest;
import com.ecomm.notification.sender.NotificationSenderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core notification service — orchestrates send + log + idempotency in a
 * single atomic transaction.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Build a {@link NotificationRequest} from the provided parameters.</li>
 *   <li>Route to the correct {@link com.ecomm.notification.sender.NotificationSender}
 *       via {@link NotificationSenderRouter#route(NotificationType)}.</li>
 *   <li>On success: persist {@link NotificationLog} with {@code status=SENT}.</li>
 *   <li>On any exception: persist {@link NotificationLog} with {@code status=FAILED}
 *       and log a WARN — the exception is swallowed so the Kafka consumer can
 *       still commit the offset and record the {@link ProcessedEvent}.</li>
 *   <li>Save {@link ProcessedEvent} to complete the idempotency record.</li>
 * </ol>
 *
 * <p>All DB writes (step 3 or 4, plus step 5) happen in one
 * {@code @Transactional} block so the log entry and the idempotency record
 * are always consistent — either both are written or neither is.
 *
 * <h3>Why swallow send exceptions?</h3>
 * Letting a send exception bubble up would cause the Kafka consumer to
 * not commit the offset, resulting in infinite re-delivery of the same
 * event. Since we record FAILED status and log the error, operators have
 * full visibility without poisoning the consumer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository  notificationLogRepository;
    private final ProcessedEventRepository   processedEventRepository;
    private final NotificationSenderRouter   senderRouter;

    /**
     * Sends a notification and logs the outcome.
     *
     * @param eventId    the Kafka envelope {@code eventId} — used as the
     *                   idempotency key in {@link ProcessedEvent}
     * @param type       notification channel (EMAIL or SMS)
     * @param recipient  email address or phone number
     * @param subject    email subject line; {@code null} for SMS
     * @param body       human-readable message body
     * @param rawPayload the raw JSON payload from the originating Kafka event
     *                   (stored in {@link NotificationLog} for debugging)
     */
    @Transactional
    public void sendAndLog(UUID eventId,
                           NotificationType type,
                           String recipient,
                           String subject,
                           String body,
                           String rawPayload) {

        NotificationRequest request = NotificationRequest.builder()
                .type(type)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .build();

        NotificationStatus status;

        try {
            senderRouter.route(type).send(request);
            status = NotificationStatus.SENT;
        } catch (Exception ex) {
            log.warn("Notification send FAILED type={} recipient={}: {}",
                    type, recipient, ex.getMessage());
            status = NotificationStatus.FAILED;
        }

        // Persist log entry — same transaction as ProcessedEvent below
        notificationLogRepository.save(NotificationLog.builder()
                .type(type)
                .recipient(recipient)
                .subject(subject)
                .payload(rawPayload)
                .status(status)
                .build());

        // Complete idempotency record
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .build());

        log.debug("Notification logged eventId={} type={} recipient={} status={}",
                eventId, type, recipient, status);
    }
}
