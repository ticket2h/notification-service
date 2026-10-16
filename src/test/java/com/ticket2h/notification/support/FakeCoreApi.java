package com.ticket2h.notification.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticket2h.notification.client.TicketInfo;

import tools.jackson.databind.json.JsonMapper;

/**
 * Thay core-service trong test: HTTP server của JDK phục vụ {@code /internal/tickets?ids=...} theo đúng hợp đồng
 * (infra/docs/contracts.md). Người dùng nằm ở {@link FakeIdentityApi}. Một instance cho cả JVM.
 */
public final class FakeCoreApi {

    /** Phải giống ticket2h.core-api.api-key trong application-test.yml. */
    public static final String API_KEY = "test-internal-key-notification-0123456789";

    public static final String EVENT_TITLE = "Sơn Tùng M-TP Live Concert 2026";
    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    public static final FakeCoreApi INSTANCE = new FakeCoreApi();

    private final HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<UUID, TicketInfo> tickets = new ConcurrentHashMap<>();
    private final List<String> apiKeysSeen = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger failuresServed = new AtomicInteger();

    private FakeCoreApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/internal/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        tickets.clear();
        apiKeysSeen.clear();
        failuresRemaining.set(0);
        failuresServed.set(0);
    }

    /** {@code n} lời gọi tới trả 503, như khi core-service đang khởi động lại. */
    public void failNext(int n) {
        failuresRemaining.set(n);
    }

    public int failuresServed() {
        return failuresServed.get();
    }

    public List<String> apiKeysSeen() {
        return apiKeysSeen;
    }

    /** Vé VALID, version 1, token QR ngẫu nhiên. */
    public TicketInfo validTicket(UUID owner, UUID orderId, String seatLabel) {
        return ticket(owner, orderId, "VALID", 1, seatLabel);
    }

    public TicketInfo ticket(UUID owner, UUID orderId, String status, int tokenVersion, String seatLabel) {
        UUID id = UUID.randomUUID();
        String qrToken = "VALID".equals(status) ? "v" + tokenVersion + "." + id + "." + UUID.randomUUID() : null;
        TicketInfo ticket = new TicketInfo(id, orderId, EVENT_ID, owner, status, tokenVersion, EVENT_TITLE,
                Instant.parse("2026-12-20T13:00:00Z"), "Asia/Ho_Chi_Minh", seatLabel == null ? "GA" : "VIP",
                seatLabel, qrToken);
        tickets.put(id, ticket);
        return ticket;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            apiKeysSeen.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")));
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                failuresServed.incrementAndGet();
                respond(exchange, 503, Map.of("code", "UNAVAILABLE"));
                return;
            }
            if (!API_KEY.equals(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"))) {
                respond(exchange, 401, Map.of("code", "UNAUTHENTICATED"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/internal/tickets")) {
                List<Map<String, Object>> body = new ArrayList<>();
                for (UUID id : ids(exchange.getRequestURI().getRawQuery())) {
                    TicketInfo t = tickets.get(id);
                    if (t != null) {
                        body.add(toJson(t));
                    }
                }
                respond(exchange, 200, body);
            } else {
                respond(exchange, 404, Map.of("code", "NOT_FOUND"));
            }
        }
    }

    /** {@code ids=a&ids=b} hoặc {@code ids=a,b}: Spring chấp nhận cả hai. */
    private static List<UUID> ids(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.split("&"))
                .filter(p -> p.startsWith("ids="))
                .flatMap(p -> Arrays.stream(URLDecoder.decode(p.substring(4), StandardCharsets.UTF_8).split(",")))
                .map(UUID::fromString)
                .toList();
    }

    /** Ghi tay từng trường cho giống hệt JSON của core-service (Instant dạng ISO-8601, null vẫn có mặt). */
    private static Map<String, Object> toJson(TicketInfo t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ticketId", t.ticketId().toString());
        m.put("orderId", t.orderId().toString());
        m.put("eventId", t.eventId().toString());
        m.put("ownerUserId", t.ownerUserId().toString());
        m.put("status", t.status());
        m.put("tokenVersion", t.tokenVersion());
        m.put("eventTitle", t.eventTitle());
        m.put("eventStartsAt", t.eventStartsAt().toString());
        m.put("eventTimeZone", t.eventTimeZone());
        m.put("ticketTypeName", t.ticketTypeName());
        m.put("seatLabel", t.seatLabel());
        m.put("qrToken", t.qrToken());
        return m;
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", status < 400 ? "application/json" : "application/problem+json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
