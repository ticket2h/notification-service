package com.ticket2h.notification.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.ticket2h.notification.config.NotificationProperties;

/** Gọi API nội bộ của identity-service: email và tên người nhận (infra/docs/contracts.md). */
@Component
public class IdentityApiClient {

    private final RestClient rest;

    IdentityApiClient(NotificationProperties properties) {
        this.rest = InternalApi.restClient(properties.identityApi());
    }

    /** Người dùng không còn tồn tại (404) thì rỗng: thử lại cũng không ra, khác với lỗi mạng. */
    public Optional<UserContact> findUser(UUID userId) {
        try {
            return Optional.ofNullable(InternalApi.call("identity-service", "lấy người dùng", () -> rest.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .body(UserContact.class)));
        } catch (InternalApiException e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
