package com.ticket2h.notification.client;

import java.util.UUID;

/** Thông tin liên hệ của người dùng, lấy từ {@code GET /internal/users/{id}} của core-service. */
public record UserContact(UUID id, String email, String fullName) {}
