package com.ticket2h.notification.client;

import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ticket2h.notification.config.NotificationProperties;

/**
 * Gọi API nội bộ của core-service: thông tin vé kèm token QR (infra/docs/contracts.md). Không log body trả về,
 * vì có token QR.
 */
@Component
public class CoreApiClient {

    private static final ParameterizedTypeReference<List<TicketInfo>> TICKET_LIST = new ParameterizedTypeReference<>() {};

    private final RestClient rest;

    CoreApiClient(NotificationProperties properties) {
        this.rest = InternalApi.restClient(properties.coreApi());
    }

    /** Vé không tồn tại thì không có trong kết quả. */
    public List<TicketInfo> tickets(List<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return List.of();
        }
        List<TicketInfo> tickets = InternalApi.call("core-service", "lấy vé", () -> rest.get()
                .uri(b -> b.path("/internal/tickets").queryParam("ids", ticketIds.toArray()).build())
                .retrieve()
                .body(TICKET_LIST));
        return tickets == null ? List.of() : tickets;
    }
}
