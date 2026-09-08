package com.ecomm.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer-only configuration for notification-service.
 *
 * <p>This service is a pure consumer — it never publishes events — so no
 * {@code ProducerFactory} or {@code KafkaTemplate} bean is defined here.
 *
 * <p>Consumer settings:
 * <ul>
 *   <li>{@code enable.auto.commit=false} — offsets committed only after the
 *       listener method returns successfully (manual RECORD ack-mode)</li>
 *   <li>{@code auto.offset.reset=earliest} — replay from the beginning on
 *       first startup so no events are silently skipped</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,               bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                        groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,               "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,              false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,          StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,        StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Container factory with manual RECORD-level acknowledgement.
     *
     * <p>The offset is only committed after the listener method returns
     * without exception — i.e. after the DB transaction (notification_log +
     * processed_event) has committed. This ensures no event is silently
     * dropped on a crash between consumption and DB write.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
