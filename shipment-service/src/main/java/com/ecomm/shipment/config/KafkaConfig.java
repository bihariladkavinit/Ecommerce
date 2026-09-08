package com.ecomm.shipment.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer and consumer configuration.
 *
 * <p><strong>Producer</strong> — used by the outbox relay to publish
 * {@code shipment.create.reply}:
 * <ul>
 *   <li>{@code acks=all} — waits for all in-sync replicas</li>
 *   <li>{@code enable.idempotence=true} — broker-side deduplication on retries</li>
 *   <li>{@code retries=3}</li>
 * </ul>
 *
 * <p><strong>Consumer</strong> — used by the command listener:
 * <ul>
 *   <li>{@code enable.auto.commit=false} — offsets committed only after
 *       the DB transaction commits (manual RECORD ack-mode)</li>
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

    // ── Producer ──────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,              bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,           StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,         StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG,                           "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,             true);
        props.put(ProducerConfig.RETRIES_CONFIG,                        3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ──────────────────────────────────────────────────────

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
     * <p>The consumer only commits an offset after the listener method returns
     * without exception — i.e. after the DB transaction committed. This ensures
     * no event is silently lost on a crash between consume and commit.
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
