package com.ticket2h.notification.repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Sổ ghi email đã gửi, chống gửi trùng theo {@code dedupe_key}. Mỗi câu lệnh tự commit (không bọc
 * transaction), vì việc gửi email nằm giữa và không được nằm trong transaction.
 */
@Repository
public class NotificationDeliveryRepository {

    static final String CHANNEL_EMAIL = "EMAIL";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    NotificationDeliveryRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Tạo dòng PENDING nếu chưa có. @return {@code true} nếu email này đã được gửi trước đó */
    public boolean alreadySent(String dedupeKey, String recipient) {
        jdbc.update("""
                INSERT INTO notification_deliveries (id, dedupe_key, channel, recipient, status, created_at)
                VALUES (:id, :dedupeKey, :channel, :recipient, 'PENDING', :now)
                ON CONFLICT (dedupe_key) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID()).addValue("dedupeKey", dedupeKey)
                .addValue("channel", CHANNEL_EMAIL).addValue("recipient", recipient)
                .addValue("now", now()));
        List<String> status = jdbc.queryForList("SELECT status FROM notification_deliveries WHERE dedupe_key = :key",
                Map.of("key", dedupeKey), String.class);
        return status.contains("SENT");
    }

    public void markSent(String dedupeKey) {
        jdbc.update("""
                UPDATE notification_deliveries SET status = 'SENT', sent_at = :now, attempts = attempts + 1
                WHERE dedupe_key = :key
                """, Map.of("now", now(), "key", dedupeKey));
    }

    public void recordFailure(String dedupeKey) {
        jdbc.update("UPDATE notification_deliveries SET attempts = attempts + 1 WHERE dedupe_key = :key",
                Map.of("key", dedupeKey));
    }

    /** {@code OffsetDateTime} UTC: kiểu mà driver PostgreSQL map chắc chắn vào {@code timestamptz}. */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
