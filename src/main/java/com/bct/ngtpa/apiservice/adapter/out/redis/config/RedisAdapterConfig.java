package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheAdminAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisStringCacheAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisCaptchaChallengeAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisCaptchaChallengeJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRateLimitAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationIdempotencyAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordIdempotencyAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordIdempotencyJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginCredentialIdempotencyAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.infrastructure.security.idempotency.HmacLoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationIdempotencyJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionCompletionLockAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisOtpChallengeJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisOtpChallengeStateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring configuration for the Redis outbound adapter.
 *
 * <p>
 * Provides a {@link LettuceConnectionFactory} wired to Redis Sentinel and an
 * optional mTLS SSL bundle, plus a {@link ReactiveRedisTemplate} with
 * plain-string
 * (UTF-8) serializers for both keys and values. All connection details are
 * externalized through {@link RedisCacheProperties} and its
 * environment-variable
 * placeholders; no connection details are hardcoded here.
 *
 * <p>
 * This configuration is active by default ({@code redis-cache.enabled=true}).
 * Set {@code REDIS_CACHE_ENABLED=false} to disable it (e.g. in local-dev
 * without
 * a Sentinel instance, or in test contexts that do not require a live Redis).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RedisCacheProperties.class,
        com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceSchemaProperties.class
})
@ConditionalOnProperty(name = "redis-cache.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisAdapterConfig {

    private final RedisCacheProperties properties;
    private final SslBundles sslBundles;

    @Bean
    public com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState authFlowSessionPersistenceCompatibilityState() {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState();
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityPort authFlowSessionPersistenceCompatibilityPort(
            ReactiveStringRedisTemplate redis,
            RedisCacheKeyFactory keyFactory) {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.RedisAuthFlowSessionPersistenceCompatibilityAdapter(
                redis, keyFactory);
    }

    @Bean
    public org.springframework.boot.ApplicationRunner authFlowSessionPersistenceCompatibilityStartupGate(
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityPort port,
            com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceSchemaProperties schema,
            com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState state) {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityStartupGate(
                port, schema, state, Duration.ofMillis(properties.getTimeoutMilliseconds()));
    }

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

    @Bean
    public RedisCacheKeyFactory redisCacheKeyFactory() {
        return new RedisCacheKeyFactory(properties.getKeyPrefix());
    }

    @Bean
    public CachePort redisStringCacheAdapter(
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        return new RedisStringCacheAdapter(reactiveRedisTemplate);
    }

    @Bean
    public CacheAdminPort redisCacheAdminAdapter(
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
            RedisCacheKeyFactory redisCacheKeyFactory) {
        return new RedisCacheAdminAdapter(reactiveRedisTemplate, redisCacheKeyFactory);
    }

    @Bean
    public RateLimitPort redisRateLimitAdapter(
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
            RedisCacheKeyFactory redisCacheKeyFactory) {
        return new RedisRateLimitAdapter(reactiveRedisTemplate, redisCacheKeyFactory);
    }

    @Bean
    public RedisLoginIdempotencyKeyFactory redisLoginIdempotencyKeyFactory(
            @Value("${redis-cache.key-prefix:ngtpa}") String keyPrefix) {
        return new RedisLoginIdempotencyKeyFactory(keyPrefix);
    }

    @Bean
    public LoginIdempotencyPersistenceIdDeriver loginIdempotencyPersistenceIdDeriver(
            @Value("${auth-workflows.login.idempotency.persistence-hmac-secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank() || configuredSecret.contains("change-me"))
            throw new IllegalStateException(
                    "auth-workflows.login.idempotency.persistence-hmac-secret is missing or invalid");
        byte[] secret;
        try {
            secret = java.util.Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("auth-workflows.login.idempotency.persistence-hmac-secret is malformed");
        }
        return new HmacLoginIdempotencyPersistenceIdDeriver(secret);
    }

    @Bean
    public LoginCredentialIdempotencyPort loginCredentialIdempotencyPort(
            RedisLoginIdempotencyKeyFactory keyFactory,
            LoginIdempotencyPersistenceIdDeriver derivation,
            ReactiveStringRedisTemplate redisTemplate) {
        return new RedisLoginCredentialIdempotencyAdapter(keyFactory, derivation, redisTemplate);
    }

    @Bean
    public RedisRegistrationIdempotencyKeyFactory redisRegistrationIdempotencyKeyFactory(
            @Value("${redis-cache.key-prefix:ngtpa}") String keyPrefix) {
        return new RedisRegistrationIdempotencyKeyFactory(keyPrefix);
    }

    @Bean
    public RedisRegistrationIdempotencyJsonMapper redisRegistrationIdempotencyJsonMapper() {
        return new RedisRegistrationIdempotencyJsonMapper();
    }

    @Bean
    public RegistrationIdempotencyPort registrationIdempotencyPort(
            RedisRegistrationIdempotencyKeyFactory keyFactory,
            RedisRegistrationIdempotencyJsonMapper jsonMapper,
            ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        return new RedisRegistrationIdempotencyAdapter(
                keyFactory,
                jsonMapper,
                reactiveStringRedisTemplate);
    }

    @Bean
    public RedisForgotPasswordIdempotencyKeyFactory redisForgotPasswordIdempotencyKeyFactory(
            @Value("${redis-cache.key-prefix:ngtpa}") String keyPrefix) {
        return new RedisForgotPasswordIdempotencyKeyFactory(keyPrefix);
    }

    @Bean
    public com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateKeyFactory redisForgotPasswordOtpCommitStateKeyFactory(
            @Value("${redis-cache.key-prefix:ngtpa}") String prefix) {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateKeyFactory(
                prefix);
    }

    @Bean
    public com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateJsonMapper redisForgotPasswordOtpCommitStateJsonMapper() {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateJsonMapper();
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort forgotPasswordOtpCommitStatePort(
            com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateKeyFactory k,
            com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateJsonMapper m,
            ReactiveStringRedisTemplate r) {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.RedisForgotPasswordOtpCommitStateAdapter(
                k, m, r);
    }

    @Bean
    public RedisForgotPasswordIdempotencyJsonMapper redisForgotPasswordIdempotencyJsonMapper() {
        return new RedisForgotPasswordIdempotencyJsonMapper();
    }

    @Bean
    public ForgotPasswordIdempotencyPort forgotPasswordIdempotencyPort(
            RedisForgotPasswordIdempotencyKeyFactory keyFactory,
            RedisForgotPasswordIdempotencyJsonMapper jsonMapper,
            ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        return new RedisForgotPasswordIdempotencyAdapter(keyFactory, jsonMapper, reactiveStringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisAuthFlowSessionJsonMapper redisAuthFlowSessionJsonMapper() {
        return new RedisAuthFlowSessionJsonMapper();
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowSessionCompletionLockPort.class)
    public AuthFlowSessionCompletionLockPort authFlowSessionCompletionLockPort(
            ReactiveStringRedisTemplate reactiveStringRedisTemplate,
            RedisCacheKeyFactory redisCacheKeyFactory) {
        return new RedisAuthFlowSessionCompletionLockAdapter(
                reactiveStringRedisTemplate,
                redisCacheKeyFactory);
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowSessionPort.class)
    public AuthFlowSessionPort authFlowSessionPort(
            CachePort cachePort,
            RedisCacheKeyFactory redisCacheKeyFactory,
            RedisAuthFlowSessionJsonMapper redisAuthFlowSessionJsonMapper,
            ReactiveStringRedisTemplate reactiveStringRedisTemplate,
            com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState compatibilityState) {
        return new RedisAuthFlowSessionAdapter(
                cachePort,
                redisCacheKeyFactory,
                redisAuthFlowSessionJsonMapper,
                new com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision.ReactiveRevisionedAuthFlowSessionRedisOperations(
                        reactiveStringRedisTemplate),
                new com.bct.ngtpa.apiservice.application.dto.session.authflow.SecureSessionRevisionGenerator(),
                compatibilityState);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisOtpChallengeJsonMapper redisOtpChallengeJsonMapper() {
        return new RedisOtpChallengeJsonMapper();
    }

    @Bean
    @ConditionalOnMissingBean(OtpChallengeStatePort.class)
    public OtpChallengeStatePort otpChallengeStatePort(
            CachePort cachePort,
            RedisCacheKeyFactory redisCacheKeyFactory,
            RedisOtpChallengeJsonMapper redisOtpChallengeJsonMapper) {
        return new RedisOtpChallengeStateAdapter(
                cachePort,
                redisCacheKeyFactory,
                redisOtpChallengeJsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisCaptchaChallengeJsonMapper redisCaptchaChallengeJsonMapper() {
        return new RedisCaptchaChallengeJsonMapper();
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaChallengePort.class)
    public CaptchaChallengePort captchaChallengePort(
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
            RedisCacheKeyFactory redisCacheKeyFactory,
            RedisCaptchaChallengeJsonMapper redisCaptchaChallengeJsonMapper) {
        return new RedisCaptchaChallengeAdapter(
                redisCacheKeyFactory,
                redisCaptchaChallengeJsonMapper,
                reactiveRedisTemplate);
    }
    // ── Internal configuration builders ──────────────────────────────────────

    private RedisSentinelConfiguration buildSentinelConfig() {
        var sentinel = properties.getSentinel();

        Set<String> nodes = sentinel.getNodes().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));

        RedisSentinelConfiguration config = new RedisSentinelConfiguration(sentinel.getMaster(), nodes);

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

    @org.springframework.context.annotation.Bean
    public com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort authFlowSessionMutationLockPort(
            org.springframework.data.redis.core.ReactiveStringRedisTemplate redis,
            com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory keys) {
        return new com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionMutationLockAdapter(redis, keys);
    }
}
