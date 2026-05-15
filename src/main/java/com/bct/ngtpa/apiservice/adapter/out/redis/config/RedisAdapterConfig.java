package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring configuration for the Redis outbound adapter.
 *
 * <p>Provides a {@link LettuceConnectionFactory} wired to Redis Sentinel and an
 * optional mTLS SSL bundle, plus a {@link ReactiveRedisTemplate} with plain-string
 * (UTF-8) serializers for both keys and values. All connection details are
 * externalized through {@link RedisCacheProperties} and its environment-variable
 * placeholders; no connection details are hardcoded here.
 *
 * <p>This configuration is active by default ({@code redis-cache.enabled=true}).
 * Set {@code REDIS_CACHE_ENABLED=false} to disable it (e.g. in local-dev without
 * a Sentinel instance, or in test contexts that do not require a live Redis).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisCacheProperties.class)
@ConditionalOnProperty(name = "redis-cache.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisAdapterConfig {

    private final RedisCacheProperties properties;
    private final SslBundles sslBundles;

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = buildSentinelConfig();
        LettuceClientConfiguration clientConfig = buildClientConfig();
        return new LettuceConnectionFactory(sentinelConfig, clientConfig);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            LettuceConnectionFactory lettuceConnectionFactory) {
        RedisSerializationContext<String, String> context = RedisSerializationContext.string();
        return new ReactiveRedisTemplate<>(lettuceConnectionFactory, context);
    }

    // ── Internal configuration builders ──────────────────────────────────────

    private RedisSentinelConfiguration buildSentinelConfig() {
        var sentinel = properties.getSentinel();

        Set<String> nodes = sentinel.getNodes().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));

        RedisSentinelConfiguration config =
                new RedisSentinelConfiguration(sentinel.getMaster(), nodes);

        if (StringUtils.hasText(properties.getPassword())) {
            config.setPassword(RedisPassword.of(properties.getPassword()));
        }
        if (StringUtils.hasText(sentinel.getPassword())) {
            config.setSentinelPassword(RedisPassword.of(sentinel.getPassword()));
        }

        return config;
    }

    private LettuceClientConfiguration buildClientConfig() {
        long timeoutMs = properties.getTimeoutMilliseconds();

        if (properties.getSsl().isEnabled()) {
            return buildSslClientConfig(timeoutMs);
        }
        return buildPlainClientConfig(timeoutMs);
    }

    private LettuceClientConfiguration buildSslClientConfig(long timeoutMs) {
        SslBundle bundle = sslBundles.getBundle(properties.getSsl().getBundle());
        SslManagerBundle managers = bundle.getManagers();

        SslOptions sslOptions = SslOptions.builder()
                .jdkSslProvider()
                .keyManager(managers.getKeyManagerFactory())
                .trustManager(managers.getTrustManagerFactory())
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .sslOptions(sslOptions)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeoutMs)))
                .build();

        return LettuceClientConfiguration.builder()
                .useSsl()
                .disablePeerVerification()
                .and()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    private LettuceClientConfiguration buildPlainClientConfig(long timeoutMs) {
        ClientOptions clientOptions = ClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeoutMs)))
                .build();

        return LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
}
