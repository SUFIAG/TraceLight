package com.sentinel.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for Phase 6: Distributed Cache (L2)
 *
 * Multi-level caching strategy:
 * - L1 Cache: Caffeine (in-process, fast)
 * - L2 Cache: Redis (distributed, shared across instances)
 *
 * Use Redis for:
 * - Multi-instance deployments
 * - Cache synchronization
 * - Session management
 * - Distributed locks
 *
 * Profile-based activation:
 * - Local dev: Caffeine only (no Redis needed)
 * - Production: Caffeine (L1) + Redis (L2)
 */
@Configuration
@ConditionalOnProperty(name = "sentinel.redis.enabled", havingValue = "true")
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * Redis connection factory using Lettuce
     * Lettuce provides async, reactive, and thread-safe operations
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        return new LettuceConnectionFactory(config);
    }

    /**
     * Redis template for direct Redis operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer (JSON)
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Object mapper for Redis JSON serialization
     * Handles Java Time API and polymorphic types
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Support Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());

        // Enable type information for polymorphic deserialization
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    /**
     * Redis Cache Manager (L2 Cache)
     *
     * This is used alongside Caffeine cache manager.
     * Spring Boot 2.1+ supports multiple cache managers via @Cacheable("cacheName")
     */
    @Bean(name = "redisCacheManager")
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(60)) // Default 1 hour TTL
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisObjectMapper())
                )
            )
            .disableCachingNullValues();

        // Specific cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User profiles - 1 hour TTL
        cacheConfigurations.put(
            CacheConfig.USER_PROFILES,
            defaultConfig.entryTtl(Duration.ofHours(1))
        );

        // Devices - 30 minutes TTL
        cacheConfigurations.put(
            CacheConfig.DEVICES,
            defaultConfig.entryTtl(Duration.ofMinutes(30))
        );

        // Fraud rules - 2 hours TTL (stable data)
        cacheConfigurations.put(
            CacheConfig.FRAUD_RULES,
            defaultConfig.entryTtl(Duration.ofHours(2))
        );

        // Organizations - 4 hours TTL (very stable)
        cacheConfigurations.put(
            CacheConfig.ORGANIZATIONS,
            defaultConfig.entryTtl(Duration.ofHours(4))
        );

        // Merchant risk profiles - 1 hour TTL
        cacheConfigurations.put(
            CacheConfig.MERCHANTS,
            defaultConfig.entryTtl(Duration.ofHours(1))
        );

        // Country risk profiles - 24 hours TTL
        cacheConfigurations.put(
            CacheConfig.COUNTRIES,
            defaultConfig.entryTtl(Duration.ofHours(24))
        );

        // Dashboard - 1 minute TTL (near real-time)
        cacheConfigurations.put(
            CacheConfig.DASHBOARD,
            defaultConfig.entryTtl(Duration.ofMinutes(1))
        );

        // Analytics - 15 minutes TTL
        cacheConfigurations.put(
            CacheConfig.ANALYTICS,
            defaultConfig.entryTtl(Duration.ofMinutes(15))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
