package com.ecomm.cart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for cart-service.
 *
 * <p>Cart data is stored as a Redis hash:
 * <pre>
 *   Key:     "cart:{userId}"          (String)
 *   HashKey: "{productId}"            (String — UUID.toString())
 *   Value:   "{qty}"                  (String — integer as string)
 * </pre>
 *
 * <p>Using {@link StringRedisSerializer} for both key and value keeps the
 * data human-readable in Redis CLI and avoids binary serialisation overhead.
 * The cart service never stores complex objects in Redis — only quantities.
 */
@Configuration
public class RedisConfig {

    /**
     * {@link RedisTemplate} with String serialisers on all four axes
     * (key, value, hash-key, hash-value).
     *
     * <p>This bean is the sole Redis access mechanism used by
     * {@link com.ecomm.cart.repository.CartRedisRepository}.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
