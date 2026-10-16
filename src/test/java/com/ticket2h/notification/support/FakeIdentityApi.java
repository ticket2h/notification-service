package com.ticket2h.notification.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * Thay identity-service trong test: HTTP server của JDK phục vụ {@code GET /internal/users/{id}} theo đúng hợp đồng
 * (infra/docs/contracts.md), kể cả các trường notification không dùng ({@code role}, {@code organizerId}).
 * Một instance cho cả JVM.
 */
public final class FakeIdentityApi {

    /** Khoá của notification-service: cùng khoá với khi gọi core (application-test.yml). */
    public static final String API_KEY = FakeCoreApi.API_KEY;

    public static final FakeIdentityApi INSTANCE = new FakeIdentityApi();

    private final HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final List<String> apiKeysSeen = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger failuresServed = new AtomicInteger();

    private FakeIdentityApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/internal/users/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        users.clear();
        apiKeysSeen.clear();
        failuresRemaining.set(0);
        failuresServed.set(0);
    }

    /** {@code n} lời gọi tới trả 503, như khi identity-service đang khởi động lại. */
    public void failNext(int n) {
        failuresRemaining.set(n);
    }

    public int failuresServed() {
        return failuresServed.get();
    }

    public List<String> apiKeysSeen() {
        return apiKeysSeen;
    }

    /** Tài khoản CUSTOMER. */
    public UUID user(String email, String fullName) {
        UUID id = UUID.randomUUID();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id.toString());
        user.put("email", email);
        user.put("fullName", fullName);
        user.put("role", "CUSTOMER");
        user.put("organizerId", null);
        users.put(id, user);
        return id;
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
            String id = exchange.getRequestURI().getPath().substring("/internal/users/".length());
            Map<String, Object> user = users.get(UUID.fromString(id));
            if (user == null) {
                respond(exchange, 404, Map.of("code", "NOT_FOUND"));
            } else {
                respond(exchange, 200, user);
            }
        }
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
