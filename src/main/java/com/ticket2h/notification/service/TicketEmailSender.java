package com.ticket2h.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.ticket2h.notification.client.TicketInfo;
import com.ticket2h.notification.config.NotificationProperties;

/** Soạn và gửi email có QR từng vé (ảnh inline). Không ghi token QR vào log. */
@Component
public class TicketEmailSender {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    private final JavaMailSender mail;
    private final QrImageRenderer qr;
    private final NotificationProperties properties;

    TicketEmailSender(JavaMailSender mail, QrImageRenderer qr, NotificationProperties properties) {
        this.mail = mail;
        this.qr = qr;
        this.properties = properties;
    }

    /** Email chứa QR của các vé còn hiệu lực trong {@code ticketList}. */
    public void sendTickets(String to, String fullName, String subject, String intro, List<TicketInfo> ticketList) {
        List<TicketInfo> valid = ticketList.stream().filter(TicketInfo::isValid).toList();
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.email().from());
            helper.setTo(to);
            helper.setSubject(subject);

            StringBuilder html = new StringBuilder()
                    .append("<p>Xin chào ").append(HtmlUtils.htmlEscape(fullName)).append(",</p>")
                    .append("<p>").append(HtmlUtils.htmlEscape(intro)).append("</p>");
            for (int i = 0; i < valid.size(); i++) {
                TicketInfo t = valid.get(i);
                html.append("<hr><p><b>").append(HtmlUtils.htmlEscape(t.eventTitle())).append("</b><br>")
                        .append(WHEN.format(t.eventStartsAt().atZone(t.zone()))).append("<br>")
                        .append("Loại vé: ").append(HtmlUtils.htmlEscape(t.ticketTypeName()));
                if (t.seatLabel() != null) {
                    html.append(" · Ghế: ").append(HtmlUtils.htmlEscape(t.seatLabel()));
                }
                html.append("<br><a href=\"").append(properties.email().publicBaseUrl())
                        .append("/tickets/").append(t.ticketId()).append("\">Xem vé</a></p>")
                        .append("<img src=\"cid:qr-").append(i).append("\" width=\"240\" height=\"240\" alt=\"QR\">");
            }
            html.append("<p><b>Không chia sẻ mã QR.</b> Ai cầm mã QR cũng vào cửa được, và mỗi vé chỉ vào được một lần.</p>");
            helper.setText(html.toString(), true);

            for (int i = 0; i < valid.size(); i++) {
                helper.addInline("qr-" + i, new ByteArrayResource(qr.png(valid.get(i).qrToken())), "image/png");
            }
            mail.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Không soạn được email vé", e);
        }
    }

    public void sendNotice(String to, String fullName, String subject, String text) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.email().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText("Xin chào " + fullName + ",\n\n" + text + "\n\nTicket2h");
            mail.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Không soạn được email thông báo", e);
        }
    }
}
