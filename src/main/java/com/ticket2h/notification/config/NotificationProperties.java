package com.ticket2h.notification.config;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Toàn bộ cấu hình {@code ticket2h.*} trong application.yml. */
@Validated
@ConfigurationProperties("ticket2h")
public record NotificationProperties(
        @Valid @NotNull Email email,
        @Valid @NotNull ServiceApi coreApi,
        @Valid @NotNull ServiceApi identityApi) {

    /** {@code publicBaseUrl}: gốc của link "Xem vé" trong email. */
    public record Email(@NotBlank String from, @NotBlank String publicBaseUrl) {}

    /**
     * API nội bộ của một service khác (core-service, identity-service). {@code apiKey} là khoá của
     * notification-service, gửi trong header X-Internal-Api-Key; cả hai service nhận cùng một khoá này.
     */
    public record ServiceApi(
            @NotNull URI baseUrl,
            @NotBlank @Size(min = 32, message = "phải dài ít nhất 32 ký tự") String apiKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {}
}
