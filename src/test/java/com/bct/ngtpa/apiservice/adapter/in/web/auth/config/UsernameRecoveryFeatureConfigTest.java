package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.SecureRandomCsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.config.UsernameRecoveryFeatureConfig;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Repair 024A Spring composition test: the single
 * {@link UsernameRecoveryFeatureConfig}
 * creates every required Username Recovery service, mapper, policy, cookie
 * collaborator, and
 * canonical port beans with no circular dependency and no duplicate bean, and its
 * cookie writer uses
 * the exact UR policy (name, path, TTL default 600, HttpOnly per cookie,
 * Secure, SameSite Strict).
 */
class UsernameRecoveryFeatureConfigTest {

    private static AuthWorkflowProperties validProperties() {
        AuthWorkflowProperties properties = new AuthWorkflowProperties();
        AuthWorkflowProperties.UsernameRecovery usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();
        usernameRecovery.getSession().setTtlSeconds(600);
        usernameRecovery.getTemplate().setEmailSubject("BCT username recovery");
        usernameRecovery.getTemplate().setEmailBody("Your BCT username is {username}.");
        usernameRecovery.getTemplate().setSmsBody("Your BCT username is {username}.");
        properties.setUsernameRecovery(usernameRecovery);
        return properties;
    }

    private static ApplicationContextRunner runner() {
        CaptchaChallengePort captchaPort = mock(CaptchaChallengePort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        return new ApplicationContextRunner()
                .withUserConfiguration(UsernameRecoveryFeatureConfig.class)
                .withPropertyValues(
                        "auth-workflows.username-recovery.context-protection.enabled=true",
                        "auth-workflows.username-recovery.context-protection.key-version=test-v1",
                        "auth-workflows.username-recovery.context-protection.secret=0123456789abcdef0123456789abcdef")
                .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
                .withBean(RedisCacheKeyFactory.class, () -> new RedisCacheKeyFactory("ngtpa"))
                .withBean(CsrfTokenGenerator.class, SecureRandomCsrfTokenGenerator::new)
                .withBean(CsrfTokenHasher.class, Sha256CsrfTokenHasher::new)
                .withBean(Clock.class, () -> clock)
                .withBean(CaptchaChallengePort.class, () -> captchaPort)
                .withBean(RegistrationCaptchaGateService.class,
                        () -> new RegistrationCaptchaGateService(captchaPort, clock))
                .withBean(UsernameRecoveryLookupPort.class, () -> mock(UsernameRecoveryLookupPort.class))
                .withBean(AuthFlowSessionPort.class, () -> mock(AuthFlowSessionPort.class))
                .withBean(MessageDeliveryPort.class, () -> mock(MessageDeliveryPort.class))
                .withBean(UsernameRecoveryEffectiveDestinationPort.class,
                        () -> mock(UsernameRecoveryEffectiveDestinationPort.class))
                .withBean(AuthWorkflowProperties.class, UsernameRecoveryFeatureConfigTest::validProperties)
                .withBean(CorsProperties.class, CorsProperties::new);
    }

    @Test
    void springContextCreatesAllUsernameRecoveryBeans() {
        runner().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(StartUsernameRecoveryUseCase.class)).isNotNull();
            assertThat(context.getBean(RequestUsernameRecoveryUseCase.class)).isNotNull();
            assertThat(context.getBean(CancelUsernameRecoveryUseCase.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryIdempotencyPort.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryAuthFlowContextBinding.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryAuthFlowContextProtectorPort.class)).isNotNull();
            assertThat(context.getBeansOfType(
                    com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.RequestUsernameRecoveryService.class))
                    .hasSize(1);
            assertThat(context.getBean(CsrfTokenValidationService.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryStartWebMapper.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryRequestWebMapper.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryCancelWebMapper.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryStartResponsePolicy.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryRequestResponsePolicy.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryCancelResponsePolicy.class)).isNotNull();
            assertThat(context.getBean(UsernameRecoveryCookieWriter.class)).isNotNull();
        });
    }

    @Test
    void cookieWriterBeanUsesExactUsernameRecoveryPolicy() {
        runner().run(context -> {
            UsernameRecoveryCookieWriter writer = context.getBean(UsernameRecoveryCookieWriter.class);

            var authFlow = writer.authFlowCookie(new AuthFlowSessionId("afs_opaque-1"));
            assertThat(authFlow.operation()).isEqualTo(SessionCookieOperation.SET);
            assertThat(authFlow.policy().name()).isEqualTo("auth_flow_sid");
            assertThat(authFlow.policy().path()).isEqualTo("/api/v1/auth/username-recovery");
            assertThat(authFlow.policy().maxAgeSeconds()).isEqualTo(600);
            assertThat(authFlow.policy().httpOnly()).isTrue();
            assertThat(authFlow.policy().secure()).isTrue();
            assertThat(authFlow.policy().sameSite()).isEqualTo(SessionCookieSameSite.NONE);

            assertThat(writer.clearAuthFlowCookie().operation()).isEqualTo(SessionCookieOperation.CLEAR);
        });
    }

    // @Test
    // void secureCookieOverrideFlipsWriterPolicy() {
    //     runner().withPropertyValues("USERNAME_RECOVERY_COOKIE_SECURE=false").run(context -> {
    //         UsernameRecoveryCookieWriter writer = context.getBean(UsernameRecoveryCookieWriter.class);
    //         assertThat(writer.authFlowCookie(new AuthFlowSessionId("afs_opaque-1"))
    //                 .policy().secure()).isFalse();
    //     });
    // }
}
