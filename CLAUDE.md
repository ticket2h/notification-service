# Ticket2h notification-service

Gửi email vé và email chuyển nhượng. Nghe Kafka `ticket2h.ticket-events`, lấy email người nhận qua API nội bộ của
identity-service và vé/token QR qua API nội bộ của core-service, gửi SMTP. Spring Boot 4, Java 25. Một trong các repo của hệ thống (cạnh `../infra`, `../core-service`);
hợp đồng với core: `../infra/docs/contracts.md`; lý do tách: `../infra/docs/adr/011-tach-notification-service.md`.

## Lệnh
- Build và chạy toàn bộ test: `./mvnw verify` (cần Docker cho Testcontainers)
- Chạy: `./mvnw spring-boot:run` (cổng 8081; cần `../infra` đang chạy, core-service ở 8080, identity-service ở 8082)

## Quy ước bắt buộc
- Consumer phải idempotent: mọi email đi qua `TicketNotificationService.deliver` với `dedupe_key` riêng.
- Payload thiếu trường hoặc hỏng → `InvalidEventPayloadException` (vào DLT ngay). Loại event lạ, trường lạ → bỏ qua.
- Lỗi tạm thời (SMTP, core-service, identity-service) → để exception bay ra cho error handler Kafka thử lại; không nuốt exception.
- Không gửi email hay gọi HTTP trong transaction (service này không dùng `@Transactional`).
- Không log token QR, header `X-Internal-Api-Key`, body trả về từ `/internal/tickets`.
- Thời gian: inject `Clock`.
- Hợp đồng với core/identity đổi thì sửa `../infra/docs/contracts.md`, `FakeCoreApi`/`FakeIdentityApi`, và service kia cùng lúc.
- Migration Flyway: không sửa file đã merge, chỉ thêm file mới. Đặt tên cho mọi constraint.
- `Dockerfile`, `.github/workflows/ci.yml` giống nhau ở cả 4 repo service (ADR-014 trong `../infra/docs/adr/`): sửa một thì sửa cả bốn.
  Thêm biến môi trường mới thì thêm vào `.env.example` ở đây và `../infra` (`.env.example`, `scripts/init-env.sh`, `docker-compose.yml`).

## Cấm
- Không sửa hoặc xoá test trong `src/test/**/acceptance/` nếu chưa hỏi. Test đỏ thì sửa code, không sửa test.
- Không thêm dependency mới nếu chưa hỏi.
- Không đọc database của service khác; cần dữ liệu thì gọi API nội bộ.

## Cấu trúc
Package theo tầng dưới `com.ticket2h.notification`: config, consumer, event, client, service, repository.
Test: `support/` (IntegrationTest, FakeCoreApi, FakeIdentityApi, FakeMailSender, Emails, KafkaTestMessages, TestcontainersConfig).
Người dùng test tạo bằng `identity.user(...)` (FakeIdentityApi), vé bằng `core.validTicket(...)` (FakeCoreApi).
