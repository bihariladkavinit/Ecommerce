package com.ecomm.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration — plain UTF-8 string serialisation for both keys and
 * values. Entries are human-readable in Redis CLI and carry no Java
 * serialisation baggage.
 *
 * <p>Keys used by this service:
 * <ul>
 *   <li>{@code product:{id}} — cached {@link com.ecomm.product.dto.response.ProductResponse}
 *       JSON, TTL = {@code product.cache.ttl-minutes} (default 10 min)</li>
 * </ul>
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
