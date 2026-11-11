package com.sentinel.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Phase 6: Event-Driven Architecture
 *
 * Event Topics:
 * 1. transaction-events - Transaction lifecycle events
 * 2. fraud-events - Fraud detection events
 * 3. case-events - Case management events
 * 4. learning-events - Machine learning feedback
 *
 * Architecture:
 * - Producer: Transactions, Alerts, Cases publish events
 * - Consumers: Analytics, Notifications, Agents consume events
 * - Enables: Horizontal scaling, event replay, audit trail
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "sentinel.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:sentinelai-consumer-group}")
    private String consumerGroupId;

    /**
     * Topic Names - Event Categories
     */
    public static class Topics {
        public static final String TRANSACTION_EVENTS = "transaction-events";
        public static final String FRAUD_EVENTS = "fraud-events";
        public static final String CASE_EVENTS = "case-events";
        public static final String LEARNING_EVENTS = "learning-events";
        public static final String NOTIFICATION_EVENTS = "notification-events";
    }

    // =================================================================
    // PRODUCER CONFIGURATION
    // =================================================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability settings
        config.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Exactly-once semantics

        // Performance settings
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024); // 32KB
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait 10ms for batching
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 64 * 1024 * 1024); // 64MB

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // =================================================================
    // CONSUMER CONFIGURATION
    // =================================================================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON deserialization settings
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.sentinel.*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.HashMap");

        // Consumer behavior
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Read from beginning if no offset
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100); // Process 100 records at a time
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000); // 5 minutes

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Concurrency - number of concurrent consumers per broker
        factory.setConcurrency(3);

        // Batch processing
        factory.setBatchListener(false); // Process one message at a time (can be changed)

        // Error handling
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

        return factory;
    }

    // =================================================================
    // TOPIC CREATION (Auto-create topics if not exist)
    // =================================================================

    /**
     * Transaction Events Topic
     * Events: TransactionIngested, TransactionValidated, TransactionAnalyzed
     */
    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(Topics.TRANSACTION_EVENTS)
                .partitions(6) // 6 partitions for parallel processing
                .replicas(1) // 1 replica (increase in production)
                .compact() // Keep latest event per key
                .build();
    }

    /**
     * Fraud Events Topic
     * Events: FraudDetected, FraudScored, AlertCreated, AlertUpdated
     */
    @Bean
    public NewTopic fraudEventsTopic() {
        return TopicBuilder.name(Topics.FRAUD_EVENTS)
                .partitions(6)
                .replicas(1)
                .compact()
                .build();
    }

    /**
     * Case Events Topic
     * Events: CaseCreated, CaseAssigned, CaseUpdated, CaseResolved
     */
    @Bean
    public NewTopic caseEventsTopic() {
        return TopicBuilder.name(Topics.CASE_EVENTS)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }

    /**
     * Learning Events Topic
     * Events: FraudConfirmed, FalsePositiveReported, PatternDiscovered
     */
    @Bean
    public NewTopic learningEventsTopic() {
        return TopicBuilder.name(Topics.LEARNING_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Notification Events Topic
     * Events: EmailNotification, SMSNotification, WebhookNotification
     */
    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(Topics.NOTIFICATION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

