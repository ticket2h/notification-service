package com.ticket2h.notification.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;

import com.ticket2h.notification.event.Topics;
import com.ticket2h.notification.support.IntegrationTest;
import com.ticket2h.notification.support.KafkaTestMessages;

/**
 * Message hỏng trên {@code ticket-events}. Ca đầu chuyển từ {@code CrashRecoveryIT} của core-service khi tách
 * notification-service (infra/docs/adr/011). Topic không bị dọn giữa các test, nên mỗi ca dùng key riêng để
 * nhận ra message của mình trong DLT. Tới DLT trong 15 giây nghĩa là không bị thử lại (thử lại kéo dài 10 phút).
 */
class TicketEventDltIT extends IntegrationTest {

    @Autowired KafkaConnectionDetails kafkaConnection;

    /** Payload thiếu trường bắt buộc là message hỏng: vào DLT ngay, không thử lại 10 phút. */
    @Test
    void event_with_missing_fields_goes_to_dlt_without_long_retries() {
        String key = "k-" + UUID.randomUUID();
        KafkaTestMessages.send(kafka, Topics.TICKET_EVENTS, key, Topics.TICKETS_ISSUED, "{\"orderId\":null}");

        ConsumerRecord<String, String> dead = KafkaTestMessages.consumeFirst(kafkaConnection,
                Topics.TICKET_EVENTS + ".DLT", r -> key.equals(r.key()), Duration.ofSeconds(15));
        assertThat(dead.value()).isEqualTo("{\"orderId\":null}");
    }

    @Test
    void non_json_message_goes_to_dlt_and_later_messages_are_still_processed() {
        String key = "k-" + UUID.randomUUID();
        KafkaTestMessages.send(kafka, Topics.TICKET_EVENTS, key, Topics.TICKETS_ISSUED, "đây không phải JSON");

        ConsumerRecord<String, String> dead = KafkaTestMessages.consumeFirst(kafkaConnection,
                Topics.TICKET_EVENTS + ".DLT", r -> key.equals(r.key()), Duration.ofSeconds(15));
        assertThat(dead.value()).isEqualTo("đây không phải JSON");

        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID orderId = UUID.randomUUID();
        publishTicketsIssued(orderId, buyer, List.of(core.validTicket(buyer, orderId, null)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
    }
}
