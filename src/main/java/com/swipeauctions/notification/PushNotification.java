package com.swipeauctions.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/** Payload sent to a user's personal STOMP queue for an in-app toast. */
public record PushNotification(
        UUID id,
        NotificationKind type,
        String title,
        String message,
        String auctionId,
        LocalDateTime timestamp) {

    public static PushNotification of(NotificationKind type, String title, String message, String auctionId) {
        return new PushNotification(UUID.randomUUID(), type, title, message, auctionId, LocalDateTime.now());
    }
}
