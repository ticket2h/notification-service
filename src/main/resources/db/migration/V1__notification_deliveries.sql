-- Sổ ghi email đã gửi, chống gửi trùng theo dedupe_key (chuyển từ core-service V112, xem infra/docs/adr/011).

CREATE TABLE notification_deliveries (
    id          uuid PRIMARY KEY,
    dedupe_key  varchar(200) NOT NULL,
    channel     varchar(20) NOT NULL,
    recipient   varchar(320) NOT NULL,
    status      varchar(20) NOT NULL,
    attempts    integer NOT NULL DEFAULT 0,
    sent_at     timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT notification_deliveries_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT uq_notification_dedupe UNIQUE (dedupe_key)
);
