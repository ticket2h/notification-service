package com.ticket2h.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import com.ticket2h.notification.client.TicketInfo;
import com.ticket2h.notification.support.Emails;
import com.ticket2h.notification.support.FakeCoreApi;
import com.ticket2h.notification.support.IntegrationTest;

/** Email vé khi core-service phát vé (TicketsIssued). */
class TicketsIssuedEmailIT extends IntegrationTest {

    @Test
    void buyer_gets_one_email_with_one_qr_per_ticket_and_each_qr_holds_the_token_from_core() throws Exception {
        UUID buyer = identity.user("buyer@test.local", "Nguyễn Văn An");
        UUID orderId = UUID.randomUUID();
        List<TicketInfo> tickets = List.of(core.validTicket(buyer, orderId, "VIP A-12"),
                core.validTicket(buyer, orderId, null), core.validTicket(buyer, orderId, null));

        publishTicketsIssued(orderId, buyer, tickets);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        MimeMessage message = mail.sent().getFirst();
        assertThat(message.getAllRecipients()[0]).hasToString("buyer@test.local");
        assertThat(message.getSubject()).isEqualTo("Vé của bạn: " + FakeCoreApi.EVENT_TITLE);
        assertThat(Emails.html(message)).contains("Nguyễn Văn An", "VIP A-12", "Không chia sẻ mã QR");
        assertThat(Emails.qrCodes(message))
                .containsExactlyInAnyOrderElementsOf(tickets.stream().map(TicketInfo::qrToken).toList());
        assertThat(core.apiKeysSeen()).isNotEmpty().containsOnly(FakeCoreApi.API_KEY);
        assertThat(identity.apiKeysSeen()).isNotEmpty().containsOnly(FakeCoreApi.API_KEY);
    }

    @Test
    void the_same_message_delivered_twice_still_sends_one_email() {
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID orderId = UUID.randomUUID();
        List<TicketInfo> tickets = List.of(core.validTicket(buyer, orderId, null));

        publishTicketsIssued(orderId, buyer, tickets);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        publishTicketsIssued(orderId, buyer, tickets);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mail.sent()).hasSize(1));
        assertThat(jdbc.queryForList("SELECT status FROM notification_deliveries", String.class)).containsExactly("SENT");
    }

    @Test
    void mail_server_failing_twice_is_retried_and_the_email_still_goes_out() {
        mail.failNext(2);
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID orderId = UUID.randomUUID();

        publishTicketsIssued(orderId, buyer, List.of(core.validTicket(buyer, orderId, null)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(mail.attempts()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT attempts FROM notification_deliveries", Integer.class)).isEqualTo(3);
    }

    /** core-service đang khởi động lại: lời gọi API lỗi thì message được thử lại, email vẫn tới. */
    @Test
    void core_service_unavailable_is_retried_until_it_answers() {
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID orderId = UUID.randomUUID();
        TicketInfo ticket = core.validTicket(buyer, orderId, null);
        core.failNext(3);

        publishTicketsIssued(orderId, buyer, List.of(ticket));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(core.failuresServed()).isEqualTo(3);
        assertThat(Emails.qrCodes(mail.sent().getFirst())).containsExactly(ticket.qrToken());
    }

    /** identity-service đang khởi động lại: chưa lấy được email người nhận thì thử lại, email vẫn tới. */
    @Test
    void identity_service_unavailable_is_retried_until_it_answers() {
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID orderId = UUID.randomUUID();
        TicketInfo ticket = core.validTicket(buyer, orderId, null);
        identity.failNext(2);

        publishTicketsIssued(orderId, buyer, List.of(ticket));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(identity.failuresServed()).isEqualTo(2);
    }

    /** Lúc gửi email, vé có thể đã được chuyển cho người khác hoặc đã dùng: chỉ gửi QR còn dùng được. */
    @Test
    void tickets_transferred_away_or_already_used_get_no_qr() {
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID friend = identity.user("friend@test.local", "Bạn");
        UUID orderId = UUID.randomUUID();
        TicketInfo kept = core.validTicket(buyer, orderId, null);
        TicketInfo givenAway = core.ticket(friend, orderId, "VALID", 2, null);
        TicketInfo used = core.ticket(buyer, orderId, "USED", 1, null);

        publishTicketsIssued(orderId, buyer, List.of(kept, givenAway, used));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(Emails.qrCodes(mail.sent().getFirst())).containsExactly(kept.qrToken());
    }

    /** Người mua không còn ở identity (404): bỏ qua, không thử lại mãi, message sau vẫn được xử lý. */
    @Test
    void unknown_buyer_is_skipped_and_later_messages_still_go_out() throws Exception {
        UUID ghost = UUID.randomUUID();
        UUID buyer = identity.user("buyer@test.local", "Khách");
        UUID ghostOrder = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        publishTicketsIssued(ghostOrder, ghost, List.of(core.validTicket(ghost, ghostOrder, null)));
        publishTicketsIssued(orderId, buyer, List.of(core.validTicket(buyer, orderId, null)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(mail.sent().getFirst().getAllRecipients()[0]).hasToString("buyer@test.local");
    }
}
