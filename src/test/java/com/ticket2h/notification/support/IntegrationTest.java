package com.ticket2h.notification.support;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticket2h.notification.client.TicketInfo;
import com.ticket2h.notification.event.TicketTransferred;
import com.ticket2h.notification.event.TicketsIssued;
import com.ticket2h.notification.event.Topics;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lớp cha cho integration test: Postgres và Kafka thật (Testcontainers), core-service giả ({@link FakeCoreApi}),
 * identity-service giả ({@link FakeIdentityApi}), mail giả ({@link FakeMailSender}). Test gửi message vào Kafka như core-service rồi xem email được gửi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected FakeMailSender mail;
    @Autowired protected KafkaTemplate<String, String> kafka;
    @Autowired protected JsonMapper json;
    @Autowired private KafkaListenerEndpointRegistry kafkaListeners;

    protected final FakeCoreApi core = FakeCoreApi.INSTANCE;
    protected final FakeIdentityApi identity = FakeIdentityApi.INSTANCE;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("ticket2h.core-api.base-url", FakeCoreApi.INSTANCE::baseUrl);
        registry.add("ticket2h.identity-api.base-url", FakeIdentityApi.INSTANCE::baseUrl);
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE notification_deliveries");
        core.reset();
        identity.reset();
        mail.reset();
        // Chờ consumer Kafka nhận partition, để message đầu tiên của test không phải chờ rebalance
        for (MessageListenerContainer container : kafkaListeners.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }
    }

    /** Gửi TicketsIssued giống hệt core-service: key = orderId, header eventType, payload JSON. */
    protected void publishTicketsIssued(UUID orderId, UUID buyer, List<TicketInfo> tickets) {
        TicketsIssued event = new TicketsIssued(orderId, buyer, tickets.stream().map(TicketInfo::ticketId).toList());
        KafkaTestMessages.send(kafka, Topics.TICKET_EVENTS, orderId.toString(), Topics.TICKETS_ISSUED,
                json.writeValueAsString(event));
    }

    /** Gửi TicketTransferred giống hệt core-service: key = ticketId. */
    protected void publishTicketTransferred(TicketTransferred event) {
        KafkaTestMessages.send(kafka, Topics.TICKET_EVENTS, event.ticketId().toString(), Topics.TICKET_TRANSFERRED,
                json.writeValueAsString(event));
    }
}
