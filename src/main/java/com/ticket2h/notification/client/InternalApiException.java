package com.ticket2h.notification.client;

/** Gọi API nội bộ thất bại (mạng, HTTP 5xx, sai khoá). Coi là lỗi tạm thời: error handler Kafka thử lại. */
public class InternalApiException extends RuntimeException {

    InternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
