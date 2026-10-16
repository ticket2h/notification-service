package com.ticket2h.notification.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nguồn thời gian duy nhất. Làm tròn xuống micro giây vì {@code timestamptz} chỉ lưu đến micro giây. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
