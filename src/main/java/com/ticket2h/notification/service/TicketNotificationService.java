package com.ticket2h.notification.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ticket2h.notification.client.CoreApiClient;
import com.ticket2h.notification.client.IdentityApiClient;
import com.ticket2h.notification.client.TicketInfo;
import com.ticket2h.notification.client.UserContact;
import com.ticket2h.notification.event.EventMessages;
import com.ticket2h.notification.event.TicketTransferred;
import com.ticket2h.notification.event.TicketsIssued;
import com.ticket2h.notification.repository.NotificationDeliveryRepository;

/**
 * Quyết định gửi email gì cho ai. Ưu tiên "gửi trùng còn hơn không gửi" (ADR-007 của core-service): sập sau
 * khi gửi nhưng trước khi đánh dấu SENT thì message được xử lý lại và khách nhận hai email. Gửi lỗi (mail server,
 * core-service hay identity-service không trả lời) thì ném exception để error handler Kafka thử lại.
 * <p>
 * Dữ liệu lấy lúc xử lý chứ không nằm trong message: email người nhận từ identity-service, vé và token QR từ
 * core-service. Token QR không bị lưu ở Kafka hay outbox, và email luôn theo trạng thái mới nhất (ADR-011, 013).
 */
@Service
public class TicketNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TicketNotificationService.class);

    private final NotificationDeliveryRepository deliveries;
    private final TicketEmailSender sender;
    private final CoreApiClient core;
    private final IdentityApiClient identity;

    TicketNotificationService(NotificationDeliveryRepository deliveries, TicketEmailSender sender, CoreApiClient core,
            IdentityApiClient identity) {
        this.deliveries = deliveries;
        this.sender = sender;
        this.core = core;
        this.identity = identity;
    }

    public void ticketsIssued(TicketsIssued event) {
        EventMessages.required(event.orderId(), "orderId");
        EventMessages.required(event.userId(), "userId");
        EventMessages.required(event.ticketIds(), "ticketIds");
        Optional<UserContact> buyer = identity.findUser(event.userId());
        if (buyer.isEmpty()) {
            log.warn("Bỏ qua TicketsIssued của đơn {}: không tìm thấy người mua", event.orderId());
            return;
        }
        // Chỉ gửi vé người mua còn đang giữ (vé đã chuyển nhượng thì QR cũ không còn dùng được)
        List<TicketInfo> owned = core.tickets(event.ticketIds()).stream()
                .filter(t -> event.userId().equals(t.ownerUserId()))
                .toList();
        String dedupeKey = "tickets-issued:" + event.orderId();
        UserContact to = buyer.get();
        deliver(dedupeKey, to.email(), () -> sender.sendTickets(to.email(), to.fullName(),
                "Vé của bạn: " + (owned.isEmpty() ? "Ticket2h" : owned.getFirst().eventTitle()),
                "Thanh toán thành công. Dưới đây là " + owned.size() + " vé của bạn.", owned));
    }

    public void ticketTransferred(TicketTransferred event) {
        EventMessages.required(event.ticketId(), "ticketId");
        EventMessages.required(event.fromUserId(), "fromUserId");
        EventMessages.required(event.toUserId(), "toUserId");
        int version = EventMessages.required(event.tokenVersion(), "tokenVersion");
        Optional<TicketInfo> ticket = core.tickets(List.of(event.ticketId())).stream().findFirst();
        Optional<UserContact> from = identity.findUser(event.fromUserId());
        Optional<UserContact> to = identity.findUser(event.toUserId());
        if (ticket.isEmpty() || from.isEmpty() || to.isEmpty()) {
            log.warn("Bỏ qua TicketTransferred của vé {}: thiếu dữ liệu", event.ticketId());
            return;
        }
        String base = "ticket-transferred:" + event.ticketId() + ":v" + version;
        // Người nhận chỉ nhận QR nếu vé vẫn thuộc họ với đúng version này (chưa chuyển tiếp lần nữa)
        List<TicketInfo> forRecipient = ticket.filter(t -> event.toUserId().equals(t.ownerUserId())
                && t.tokenVersion() == version).stream().toList();
        String title = ticket.get().eventTitle();
        deliver(base + ":to", to.get().email(), () -> sender.sendTickets(to.get().email(), to.get().fullName(),
                "Bạn nhận được vé: " + title, from.get().fullName() + " đã chuyển vé cho bạn.", forRecipient));
        deliver(base + ":from", from.get().email(), () -> sender.sendNotice(from.get().email(), from.get().fullName(),
                "Bạn đã chuyển vé: " + title,
                "Vé " + ticket.get().ticketTypeName() + " đã được chuyển cho " + to.get().email()
                        + ". Mã QR cũ của vé này không còn sử dụng được."));
    }

    private void deliver(String dedupeKey, String recipient, Runnable send) {
        if (deliveries.alreadySent(dedupeKey, recipient)) {
            return;
        }
        try {
            send.run();
        } catch (RuntimeException e) {
            deliveries.recordFailure(dedupeKey);
            throw e;
        }
        deliveries.markSent(dedupeKey);
    }
}
