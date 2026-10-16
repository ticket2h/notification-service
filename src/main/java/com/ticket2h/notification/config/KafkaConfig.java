package com.ticket2h.notification.config;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.ticket2h.notification.event.InvalidEventPayloadException;
import com.ticket2h.notification.event.Topics;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    /** Cùng số partition với core-service. DLT cùng số partition với topic gốc (recoverer gửi vào partition cùng số). */
    private static final int PARTITIONS = 3;
    public static final String DLT_SUFFIX = ".DLT";

    /**
     * core-service tạo topic {@code ticket-events} vì nó gửi message vào đó. Service này cũng khai báo, để khởi
     * động trước core thì topic vẫn có đủ 3 partition thay vì broker tự tạo 1 partition. Khai báo trùng không
     * sao: topic đã có thì KafkaAdmin bỏ qua. DLT của topic này thuộc về service này, vì service này consume nó.
     */
    @Bean
    KafkaAdmin.NewTopics notificationTopics() {
        return new KafkaAdmin.NewTopics(topic(Topics.TICKET_EVENTS), topic(Topics.TICKET_EVENTS + DLT_SUFFIX));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Lỗi trong listener (mail server sập, core-service không trả lời): thử lại có backoff (0,5s → 1s → 2s … tối
     * đa 30s mỗi lần) trong tối đa 10 phút, rồi đẩy sang {@code <topic>.DLT}. Message hỏng thì thử lại cũng vô
     * ích, đẩy thẳng sang DLT. Spring Boot tự gắn bean CommonErrorHandler này vào listener container.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        var backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(30_000);
        backOff.setMaxElapsedTime(Duration.ofMinutes(10).toMillis());

        // Ghi rõ tên topic: Spring Kafka 4 đổi hậu tố mặc định thành "-dlt"; ta giữ "<topic>.DLT", cùng partition
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventPayloadException.class);
        return handler;
    }
}
