package com.ticket2h.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TicketInfoTest {

    @Test
    void to_string_never_prints_the_qr_token() {
        TicketInfo ticket = new TicketInfo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "VALID", 1, "Show", Instant.EPOCH, "Asia/Ho_Chi_Minh", "GA", null, "secret-qr-token");

        assertThat(ticket.toString()).doesNotContain("secret-qr-token").contains("qrToken=***");
    }

    @Test
    void only_valid_tickets_with_a_token_are_valid() {
        assertThat(ticket("VALID", "t").isValid()).isTrue();
        assertThat(ticket("VALID", null).isValid()).isFalse();
        assertThat(ticket("USED", null).isValid()).isFalse();
        assertThat(ticket("SOME_NEW_STATUS", "t").isValid()).isFalse();
    }

    private static TicketInfo ticket(String status, String qrToken) {
        return new TicketInfo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status, 1,
                "Show", Instant.EPOCH, "Asia/Ho_Chi_Minh", "GA", null, qrToken);
    }
}
