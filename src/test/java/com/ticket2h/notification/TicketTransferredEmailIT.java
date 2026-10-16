package com.ticket2h.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import com.ticket2h.notification.client.TicketInfo;
import com.ticket2h.notification.event.TicketTransferred;
import com.ticket2h.notification.support.Emails;
import com.ticket2h.notification.support.FakeCoreApi;
import com.ticket2h.notification.support.IntegrationTest;

/** Email khi chuyển nhượng vé (TicketTransferred): người nhận có QR mới, người chuyển nhận thông báo. */
class TicketTransferredEmailIT extends IntegrationTest {

    @Test
    void both_people_get_one_email_each_and_only_the_recipient_gets_the_new_qr() throws Exception {
        UUID from = identity.user("from@test.local", "Người chuyển");
        UUID to = identity.user("to@test.local", "Người nhận");
        TicketInfo ticket = core.ticket(to, UUID.randomUUID(), "VALID", 2, null);
        TicketTransferred event = transferred(ticket, from, to, 2);

        publishTicketTransferred(event);
        publishTicketTransferred(event);   // message trùng

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(2));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mail.sent()).hasSize(2));
        MimeMessage toRecipient = sentTo("to@test.local");
        MimeMessage toSender = sentTo("from@test.local");
        assertThat(toRecipient.getSubject()).isEqualTo("Bạn nhận được vé: " + FakeCoreApi.EVENT_TITLE);
        assertThat(Emails.qrCodes(toRecipient)).containsExactly(ticket.qrToken());
        assertThat(toSender.getSubject()).isEqualTo("Bạn đã chuyển vé: " + FakeCoreApi.EVENT_TITLE);
        assertThat(Emails.plainText(toSender)).contains("Mã QR cũ của vé này không còn sử dụng được");
        assertThat(Emails.qrCodes(toSender)).isEmpty();
    }

    /** Người nhận đã chuyển tiếp vé cho người thứ ba trước khi email kịp gửi: không gửi QR của người khác. */
    @Test
    void recipient_gets_no_qr_when_the_ticket_has_already_moved_on() {
        UUID from = identity.user("from@test.local", "Người chuyển");
        UUID to = identity.user("to@test.local", "Người nhận");
        UUID third = identity.user("third@test.local", "Người thứ ba");
        TicketInfo ticketNow = core.ticket(third, UUID.randomUUID(), "VALID", 3, null);

        publishTicketTransferred(transferred(ticketNow, from, to, 2));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(2));
        assertThat(Emails.qrCodes(sentTo("to@test.local"))).isEmpty();
    }

    private static TicketTransferred transferred(TicketInfo ticket, UUID from, UUID to, int version) {
        return new TicketTransferred(ticket.ticketId(), ticket.eventId(), from, to, version,
                Instant.parse("2026-11-01T12:00:00Z"));
    }

    private MimeMessage sentTo(String email) {
        return mail.sent().stream().filter(m -> {
            try {
                return m.getAllRecipients()[0].toString().equals(email);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).findFirst().orElseThrow();
    }
}
