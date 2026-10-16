package com.ticket2h.notification.event;

import java.util.List;
import java.util.UUID;

/** Đơn {@code orderId} của {@code userId} đã được phát các vé {@code ticketIds}. Không chứa token QR. */
public record TicketsIssued(UUID orderId, UUID userId, List<UUID> ticketIds) {}
