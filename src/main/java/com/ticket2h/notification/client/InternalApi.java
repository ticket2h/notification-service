package com.ticket2h.notification.client;

import java.net.http.HttpClient;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.ticket2h.notification.config.NotificationProperties;

/**
 * Phần chung của các client gọi API nội bộ (core-service, identity-service): timeout, header
 * {@value #API_KEY_HEADER}, đổi lỗi HTTP thành {@link InternalApiException}. Không log body trả về.
 */
final class InternalApi {

    static final String API_KEY_HEADER = "X-Internal-Api-Key";

    static RestClient restClient(NotificationProperties.ServiceApi config) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(config.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(config.readTimeout());
        return RestClient.builder()
                .baseUrl(config.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, config.apiKey())
                .build();
    }

    /** Lỗi mạng hay HTTP lỗi → {@link InternalApiException} (listener để bay ra, Kafka thử lại sau). */
    static <T> T call(String service, String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                throw new InternalApiException(service + " từ chối khoá API nội bộ (" + action + "): kiểm tra "
                        + "INTERNAL_API_KEY_NOTIFICATION ở hai service", e);
            }
            throw new InternalApiException(service + " " + action + " trả HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new InternalApiException("Không gọi được " + service + " (" + action + "): " + e.getMessage(), e);
        }
    }

    private InternalApi() {}
}
