package com.ticket2h.notification.client;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Vé lấy từ {@code GET /internal/tickets} của core-service. {@code qrToken} chỉ có khi vé VALID.
 * {@code status} để dạng chuỗi: core thêm trạng thái mới thì service này vẫn đọc được (chỉ quan tâm VALID).
 */
public record TicketInfo(
        UUID ticketId,
        UUID orderId,
        UUID eventId,
        UUID ownerUserId,
        String status,
        int tokenVersion,
        String eventTitle,
        Instant eventStartsAt,
        String eventTimeZone,
        String ticketTypeName,
        String seatLabel,
        String qrToken) {

    public static final String VALID = "VALID";

    public boolean isValid() {
        return VALID.equals(status) && qrToken != null;
    }

    public ZoneId zone() {
        return ZoneId.of(eventTimeZone);
    }

    /** Không in token QR: record mặc định in mọi trường, mà object này có thể lọt vào log hay message lỗi. */
    @Override
    public String toString() {
        return "TicketInfo[ticketId=" + ticketId + ", ownerUserId=" + ownerUserId + ", status=" + status
                + ", tokenVersion=" + tokenVersion + ", qrToken=" + (qrToken == null ? "null" : "***") + "]";
    }
}
