package com.ticket2h.notification.event;

/**
 * Topic, loại event và header mà service này đọc. Bản sao phía consumer của hợp đồng trong infra/docs/contracts.md;
 * phía producer là {@code Topics} trong core-service. Hai bên không dùng chung thư viện: đổi hợp đồng thì sửa
 * tài liệu và cả hai service, test hợp đồng ở hai phía sẽ báo nếu lệch.
 */
public final class Topics {

    public static final String TICKET_EVENTS = "ticket2h.ticket-events";

    public static final String TICKETS_ISSUED = "TicketsIssued";
    public static final String TICKET_TRANSFERRED = "TicketTransferred";

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private Topics() {}
}
