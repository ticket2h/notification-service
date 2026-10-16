package com.ticket2h.notification.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ticket2h.notification.event.EventMessages;
import com.ticket2h.notification.event.TicketTransferred;
import com.ticket2h.notification.event.TicketsIssued;
import com.ticket2h.notification.event.Topics;
import com.ticket2h.notification.service.TicketNotificationService;

/**
 * Consumer {@code ticket2h.ticket-events}. Exception bay ra thì error handler (KafkaConfig) thử lại rồi đẩy
 * sang DLT; message hỏng vào DLT ngay. Loại event lạ thì bỏ qua: core thêm event mới không làm service này hỏng.
 */
@Component
class TicketEventsListener {

    private final EventMessages messages;
    private final TicketNotificationService notifications;

    TicketEventsListener(EventMessages messages, TicketNotificationService notifications) {
        this.messages = messages;
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.TICKET_EVENTS, groupId = "notification-service")
    void onTicketEvent(ConsumerRecord<String, String> record) {
        switch (messages.eventType(record)) {
            case Topics.TICKETS_ISSUED -> notifications.ticketsIssued(messages.payload(record, TicketsIssued.class));
            case Topics.TICKET_TRANSFERRED ->
                    notifications.ticketTransferred(messages.payload(record, TicketTransferred.class));
            default -> { }
        }
    }
}
