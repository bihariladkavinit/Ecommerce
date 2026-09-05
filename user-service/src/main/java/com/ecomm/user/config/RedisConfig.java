package com.ecomm.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 *
 * <p>A single {@link RedisTemplate}&lt;String, String&gt; is sufficient for this
 * service's usage patterns:
 * <ul>
 *   <li>{@code blacklist:{jti}} — logout token blacklist, TTL = remaining token life</li>
 *   <li>{@code user:{id}}       — profile cache, TTL 15 min</li>
 *   <li>{@code refresh:{hash}}  — optional fast-revocation check (main store is DB)</li>
 * </ul>
 *
 * Both key and value are stored as plain UTF-8 strings so entries are human-readable
 * in Redis CLI and don't carry Java serialisation baggage.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
