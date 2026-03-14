package com.lmg.online.chatbot.ai.project.doc.vector.config.chroma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * Redis infrastructure configuration.
 *
 * Exposes a single JedisPooled bean used by:
 *  - VectorStoreFactoryRedis   (multi-tenant vector store creation)
 *  - MultiTenantVectorServiceRedis (index management / document deletion)
 *  - MultiTenantPdfService     (key scanning for document listing)
 *
 * Connection properties are read from application.properties:
 *   spring.data.redis.host     (env: REDIS_HOST)
 *   spring.data.redis.port     (env: REDIS_PORT, default 6379)
 *   spring.data.redis.username (env: REDIS_USERNAME, default "default")
 *   spring.data.redis.password (env: REDIS_PASSWORD)
 */
@Configuration
@Slf4j
public class MultiTenantVectorStoreConfigRedis {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.username:default}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public JedisPooled jedisPooled() {
        log.info("🔗 Creating Redis connection → {}:{}", redisHost, redisPort);

        boolean hasPassword = redisPassword != null && !redisPassword.trim().isEmpty();
        boolean hasUsername = redisUsername != null
                && !redisUsername.trim().isEmpty()
                && !"default".equals(redisUsername.trim());

        JedisPooled jedis;
        if (hasPassword && hasUsername) {
            log.info("🔐 Redis: username + password auth (user={})", redisUsername);
            jedis = new JedisPooled(redisHost, redisPort, redisUsername, redisPassword);
        } else if (hasPassword) {
            log.info("🔐 Redis: password-only auth");
            jedis = new JedisPooled(redisHost, redisPort, "default", redisPassword);
        } else {
            log.info("🔓 Redis: no auth");
            jedis = new JedisPooled(redisHost, redisPort);
        }

        try {
            String pong = jedis.ping();
            log.info("✅ Redis connection OK: {}", pong);
        } catch (Exception e) {
            log.error("❌ Redis connection failed: {}", e.getMessage());
        }

        return jedis;
    }
}
