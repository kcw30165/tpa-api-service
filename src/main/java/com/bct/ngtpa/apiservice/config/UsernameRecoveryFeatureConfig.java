package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.UsernameRecoveryAuthFlowContextProtectionProperties;
import com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery.RedisUsernameRecoveryIdempotencyAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery.RedisUsernameRecoveryIdempotencyJsonMapper;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.CancelUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.RequestUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.StartUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelFingerprintGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestFingerprintGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartFingerprintGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Repair 024A Spring composition for the Username Recovery controller
 * prerequisites.
 *
 * <p>
 * Wires the Username Recovery runtime beans around the generic auth-flow
 * envelope, typed
 * protected auth-flow context, Redis-backed idempotency port, CSRF
 * validation/reservation services,
 * the three use-case services, the
 * stateless web mappers and response policies, and the Spring-free
 * {@link UsernameRecoveryCookieWriter} with the exact UR cookie policy (path
 * {@code /api/v1/auth/username-recovery}, Max-Age = UR session TTL, HttpOnly
 * per cookie, Secure
 * from {@code USERNAME_RECOVERY_COOKIE_SECURE} defaulting to {@code true},
 * SameSite Strict, no
 * Domain). Every bean is declared {@code @ConditionalOnMissingBean} so a later
 * task (024/025) may
 * override any single bean without a duplicate-definition failure.
 *
 * <p>
 * Location note: this class lives in the global {@code config} package because
 * it legitimately
 * composes {@code adapter.out} Redis adapters; the committed
 * {@code adapter.in must not depend on adapter.out} architecture rule prevents
 * placing it under
 * {@code adapter/in/web/auth/config}.
 */
@Configuration
public class UsernameRecoveryFeatureConfig {

    private static final String USERNAME_RECOVERY_COOKIE_PATH = "/api/v1/auth/username-recovery";

    @Bean
    @ConditionalOnMissingBean
    public RedisUsernameRecoveryIdempotencyJsonMapper redisUsernameRecoveryIdempotencyJsonMapper() {
        return new RedisUsernameRecoveryIdempotencyJsonMapper();
    }

    @Bean
    @ConditionalOnMissingBean(UsernameRecoveryIdempotencyPort.class)
    public UsernameRecoveryIdempotencyPort usernameRecoveryIdempotencyPort(
            ReactiveStringRedisTemplate redis,
            RedisCacheKeyFactory keyFactory,
            RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper) {
        return new RedisUsernameRecoveryIdempotencyAdapter(redis, keyFactory, jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryAuthFlowContextProtectionProperties usernameRecoveryAuthFlowContextProtectionProperties(
            @Value("${auth-workflows.username-recovery.context-protection.enabled:true}") boolean enabled,
            @Value("${auth-workflows.username-recovery.context-protection.key-version:${USERNAME_RECOVERY_CONTEXT_KEY_VERSION:v1}}") String keyVersion,
            @Value("${auth-workflows.username-recovery.context-protection.secret:${USERNAME_RECOVERY_CONTEXT_SECRET:}}") String secret) {
        return new UsernameRecoveryAuthFlowContextProtectionProperties(enabled, keyVersion, secret);
    }

    @Bean
    @ConditionalOnMissingBean(UsernameRecoveryAuthFlowContextProtectorPort.class)
    public UsernameRecoveryAuthFlowContextProtectorPort usernameRecoveryAuthFlowContextProtectorPort(
            UsernameRecoveryAuthFlowContextProtectionProperties properties) {
        return new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
                properties.keyVersion(), properties.secret());
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryAuthFlowContextBinding usernameRecoveryAuthFlowContextBinding(
            UsernameRecoveryAuthFlowContextProtectorPort protector) {
        return new UsernameRecoveryAuthFlowContextBinding(protector);
    }

    @Bean
    @ConditionalOnMissingBean
    public CsrfTokenValidationService csrfTokenValidationService(CsrfTokenHasher csrfTokenHasher) {
        return new CsrfTokenValidationService(csrfTokenHasher);
    }


    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryStartValidator usernameRecoveryStartValidator() {
        return new UsernameRecoveryStartValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryStartFingerprintGenerator usernameRecoveryStartFingerprintGenerator() {
        return new UsernameRecoveryStartFingerprintGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryRequestFingerprintGenerator usernameRecoveryRequestFingerprintGenerator() {
        return new UsernameRecoveryRequestFingerprintGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryCancelFingerprintGenerator usernameRecoveryCancelFingerprintGenerator() {
        return new UsernameRecoveryCancelFingerprintGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(StartUsernameRecoveryUseCase.class)
    public StartUsernameRecoveryUseCase startUsernameRecoveryUseCase(
            UsernameRecoveryStartValidator validator,
            UsernameRecoveryStartFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            RegistrationCaptchaGateService captchaGateService,
            UsernameRecoveryLookupPort lookupPort,
            AuthFlowSessionPort authFlowSessionPort,
            UsernameRecoveryAuthFlowContextBinding contextBinding,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher,
            Clock clock,
            AuthWorkflowProperties properties) {
        return new StartUsernameRecoveryService(
                validator,
                fingerprintGenerator,
                idempotencyPort,
                captchaGateService,
                lookupPort,
                authFlowSessionPort,
                contextBinding,
                csrfTokenGenerator,
                csrfTokenHasher,
                clock,
                usernameRecoverySessionTtlSeconds(properties));
    }

    @Bean
    @ConditionalOnMissingBean(RequestUsernameRecoveryUseCase.class)
    public RequestUsernameRecoveryUseCase requestUsernameRecoveryUseCase(
            UsernameRecoveryRequestFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            UsernameRecoveryAuthFlowContextBinding contextBinding,
            CsrfTokenValidationService csrfTokenValidationService,
            UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort,
            AuthFlowSessionPort authFlowSessionPort,
            MessageDeliveryPort messageDeliveryPort,
            CorsProperties corsProperties,
            Clock clock,
            AuthWorkflowProperties properties) {
        return new RequestUsernameRecoveryService(
                fingerprintGenerator,
                idempotencyPort,
                contextBinding,
                csrfTokenValidationService,
                effectiveDestinationPort,
                authFlowSessionPort,
                messageDeliveryPort,
                corsProperties.getAllowedOrigins(),
                clock,
                usernameRecoverySessionTtlSeconds(properties),
                requiredUsernameRecovery(properties).getTemplate().getEmailSubject(),
                requiredUsernameRecovery(properties).getTemplate().getEmailBody(),
                requiredUsernameRecovery(properties).getTemplate().getSmsBody());
    }

    @Bean
    @ConditionalOnMissingBean(CancelUsernameRecoveryUseCase.class)
    public CancelUsernameRecoveryUseCase cancelUsernameRecoveryUseCase(
            UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            AuthFlowSessionPort authFlowSessionPort,
            UsernameRecoveryAuthFlowContextBinding contextBinding,
            CsrfTokenValidationService csrfTokenValidationService,
            CorsProperties corsProperties,
            Clock clock,
            AuthWorkflowProperties properties) {
        return new CancelUsernameRecoveryService(
                fingerprintGenerator,
                idempotencyPort,
                authFlowSessionPort,
                contextBinding,
                csrfTokenValidationService,
                corsProperties.getAllowedOrigins(),
                clock,
                usernameRecoverySessionTtlSeconds(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryStartWebMapper usernameRecoveryStartWebMapper() {
        return new UsernameRecoveryStartWebMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryRequestWebMapper usernameRecoveryRequestWebMapper() {
        return new UsernameRecoveryRequestWebMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryCancelWebMapper usernameRecoveryCancelWebMapper() {
        return new UsernameRecoveryCancelWebMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryStartResponsePolicy usernameRecoveryStartResponsePolicy() {
        return new UsernameRecoveryStartResponsePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryRequestResponsePolicy usernameRecoveryRequestResponsePolicy() {
        return new UsernameRecoveryRequestResponsePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryCancelResponsePolicy usernameRecoveryCancelResponsePolicy() {
        return new UsernameRecoveryCancelResponsePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public UsernameRecoveryCookieWriter usernameRecoveryCookieWriter(
            AuthWorkflowProperties properties,
            @Value("${USERNAME_RECOVERY_COOKIE_SECURE:true}") boolean secure) {
        int ttlSeconds = usernameRecoverySessionTtlSeconds(properties);
        SessionCookiePolicy authFlowPolicy = new SessionCookiePolicy(
                "auth_flow_sid",
                USERNAME_RECOVERY_COOKIE_PATH,
                ttlSeconds,
                true,
                secure,
                SessionCookieSameSite.STRICT);
        return new UsernameRecoveryCookieWriter(authFlowPolicy);
    }

    private static int usernameRecoverySessionTtlSeconds(AuthWorkflowProperties properties) {
        return requiredUsernameRecovery(properties).getSession().getTtlSeconds();
    }

    private static AuthWorkflowProperties.UsernameRecovery requiredUsernameRecovery(
            AuthWorkflowProperties properties) {
        if (properties.getUsernameRecovery() == null) {
            throw new IllegalStateException("auth-workflows.username-recovery configuration is required");
        }
        return properties.getUsernameRecovery();
    }
}
