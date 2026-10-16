# Ticket2h notification-service

[![CI](https://github.com/ticket2h/notification-service/actions/workflows/ci.yml/badge.svg)](https://github.com/ticket2h/notification-service/actions/workflows/ci.yml)

Gửi email cho khách: email vé có QR khi đơn được phát vé, email cho cả hai người khi chuyển nhượng vé. Nghe Kafka,
không có API cho người dùng. Spring Boot 4.1, Java 25, Kafka 4, PostgreSQL 17, Flyway. Cổng 8081 (chỉ có
`/actuator/health`).

Đây là microservice đầu tiên tách ra khỏi lõi (core-service), xem [infra/docs/adr/011-tach-notification-service.md](https://github.com/ticket2h/infra/blob/main/docs/adr/011-tach-notification-service.md).

## Làm gì

```
Kafka ticket2h.ticket-events ──► TicketEventsListener
                                   │ TicketsIssued / TicketTransferred (chỉ có id)
                                   ▼
                          TicketNotificationService
                           ├─► IdentityApiClient: GET /internal/users/{id}   (identity-service: email, tên)
                           ├─► CoreApiClient: GET /internal/tickets?ids=     (core-service: vé, token QR)
                           ├─► NotificationDeliveryRepository: đã gửi chưa? (chống gửi trùng)
                           └─► TicketEmailSender: vẽ QR (ZXing), gửi SMTP (Mailpit khi chạy local)
```

- **Idempotent**: bảng `notification_deliveries` unique theo `dedupe_key`; message trùng không gửi thêm email.
- **Gửi trùng còn hơn không gửi**: sập sau khi gửi mà chưa ghi SENT thì lần sau gửi lại.
- **Lỗi tạm thời** (mail server, core-service hay identity-service không trả lời): ném exception, Kafka thử lại lùi dần trong 10 phút rồi
  đẩy vào `ticket2h.ticket-events.DLT`. **Message hỏng** (không phải JSON, thiếu trường): vào DLT ngay.
- **Token QR** chỉ ở trong bộ nhớ lúc soạn email: không ghi log, không lưu database (`TicketInfo.toString` che token).

Hợp đồng với core-service và identity-service (topic, trường JSON, API nội bộ): [infra/docs/contracts.md](https://github.com/ticket2h/infra/blob/main/docs/contracts.md).

## Cấu trúc

Service nhỏ, nên package chia theo **tầng kỹ thuật**:

```
src/main/java/com/ticket2h/notification/
├── NotificationServiceApplication.java
├── config/       NotificationProperties, KafkaConfig (topic, DLT, thử lại), ClockConfig
├── consumer/     TicketEventsListener: nhận message Kafka, chuyển cho service
├── event/        TicketsIssued, TicketTransferred, Topics, EventMessages: hợp đồng phía consumer
├── client/       CoreApiClient, IdentityApiClient, InternalApi (timeout, khoá, đổi lỗi), UserContact, TicketInfo
├── service/      TicketNotificationService (gửi gì cho ai), TicketEmailSender, QrImageRenderer
└── repository/   NotificationDeliveryRepository: sổ chống gửi trùng (JDBC)
src/main/resources/db/migration/V1__notification_deliveries.sql
```

## Chạy

Cần PostgreSQL, Kafka, Mailpit, biến ở [`.env.example`](.env.example), và core-service, identity-service đang chạy để lấy
dữ liệu (không có thì message nằm chờ, thử lại tới 10 phút). Cách nhanh nhất: clone repo [infra](https://github.com/ticket2h/infra) cạnh repo
này (hướng dẫn từ máy trống: [infra/docs/chay-tren-may-moi.md](https://github.com/ticket2h/infra/blob/main/docs/chay-tren-may-moi.md)):

```bash
(cd ../infra && scripts/init-env.sh && docker compose --profile apps up -d)   # hạ tầng + các service khác từ image
(cd ../infra && docker compose stop notification-service)                     # nhường cổng 8081 cho bản đang sửa
./mvnw spring-boot:run          # :8081
```

Service đọc secret từ `../infra/.env` nếu repo [infra](https://github.com/ticket2h/infra) nằm cạnh repo này, từ `.env` của chính repo
(mẫu: [`.env.example`](.env.example), chỉ gồm biến service này cần), hoặc từ biến môi trường của máy.

Image Docker: `ghcr.io/ticket2h/notification-service` (CI build mỗi lần push lên `main`). Build tại máy: `docker build -t notification-service .`
(không cần JDK). Chạy cả hệ thống từ image: `docker compose --profile apps up -d` trong repo infra.

| Cấu hình | Mặc định | Ý nghĩa |
|---|---|---|
| `spring.datasource.*` | `localhost:5432/notification`, user `notification` | Database riêng |
| `spring.mail.host/port` | `localhost:1025` | SMTP (Mailpit) |
| `ticket2h.core-api.base-url` | `http://localhost:8080` | core-service |
| `ticket2h.core-api.api-key` | `${INTERNAL_API_KEY_NOTIFICATION}` | Khoá gọi `/internal/**` của core |
| `ticket2h.identity-api.base-url` | `http://localhost:8082` | identity-service |
| `ticket2h.identity-api.api-key` | `${INTERNAL_API_KEY_NOTIFICATION}` | Cùng khoá: khoá là của notification, core và identity đều nhận |
| `ticket2h.email.public-base-url` | `http://localhost:8080` | Gốc của link "Xem vé" trong email |

## Test

```bash
./mvnw verify      # 2 unit test + 11 integration test
```

Test chạy PostgreSQL và Kafka thật (Testcontainers, cần Docker). core-service được thay bằng `FakeCoreApi`,
identity-service bằng `FakeIdentityApi` (HTTP server của JDK, trả JSON đúng hợp đồng), mail server bằng `FakeMailSender`. Test gửi message vào Kafka giống hệt
core-service rồi kiểm tra email, kể cả **giải mã ảnh QR trong email** và so với token core trả về.

| Test | Kiểm tra |
|---|---|
| `TicketsIssuedEmailIT` | 1 email, đủ QR; message trùng; mail lỗi 2 lần; core-service lỗi 3 lần; identity-service lỗi 2 lần; vé đã chuyển/đã dùng không có QR; người mua không còn tồn tại |
| `TicketTransferredEmailIT` | Hai người, mỗi người 1 email; chỉ người nhận có QR; vé đã chuyển tiếp thì không gửi QR |
| `acceptance/TicketEventDltIT` | Message hỏng vào DLT ngay; message sau vẫn được xử lý |
| `client/TicketInfoTest` | `toString` không in token QR |
