package com.ecomm.notification.sender;

import com.ecomm.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable value object carrying everything a {@link NotificationSender}
 * needs to deliver a single notification.
 *
 * <p>Using Lombok {@code @Value} (immutable) + {@code @Builder} keeps
 * construction fluent while preventing accidental mutation inside senders.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code type}      — EMAIL or SMS; drives routing in {@link NotificationSenderRouter}</li>
 *   <li>{@code recipient} — email address (EMAIL) or phone number (SMS)</li>
 *   <li>{@code subject}   — email subject line; {@code null} for SMS</li>
 *   <li>{@code body}      — human-readable message body</li>
 * </ul>
 */
@Value
@Builder
public class NotificationRequest {

    NotificationType type;
    String           recipient;
    String           subject;   // null for SMS
    String           body;
}
