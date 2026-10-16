package com.ticket2h.notification.event;

import java.time.Instant;
import java.util.UUID;

/** Vé {@code ticketId} đã chuyển từ {@code fromUserId} sang {@code toUserId}; QR mới có {@code tokenVersion} này. */
public record TicketTransferred(UUID ticketId, UUID eventId, UUID fromUserId, UUID toUserId, Integer tokenVersion,
        Instant transferredAt) {}
