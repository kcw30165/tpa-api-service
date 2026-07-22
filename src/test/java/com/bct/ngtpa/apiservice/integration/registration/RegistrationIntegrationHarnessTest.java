package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.CaptchaChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.CreateCaptchaChallengeService;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCatalog;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RegistrationIntegrationHarnessTest.TestApplication.class,
        properties = {
                "api.security.require-authentication=false",
                "redis-cache.enabled=false",
                "spring.main.banner-mode=off"
        })
@AutoConfigureWebTestClient
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationIntegrationHarnessTest {

    private static final String CHALLENGE_ID = "cap_abcdefghijklmnop";
    private static final String CAPTCHA_ANSWER = "AbC12312";
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InMemoryCaptchaChallengePort challengePort;

    @Test
    @RegistrationTestCase("TC-001")
    void captchaChallengeProofOfLifeRunsRandomPortHttpAndRealApplicationService() {
        EntityExchangeResult<byte[]> exchange = webTestClient.post()
                .uri("/api/v1/auth/captcha/challenge")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "0041-tc-001-request")
                .header("Accept-Language", "en")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        RequestCorrelation.REQUEST_ID_HEADER,
                        "0041-tc-001-request")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.result.nextStep").isEqualTo("NONE")
                .jsonPath("$.result.data.captchaChallengeId").isEqualTo(CHALLENGE_ID)
                .jsonPath("$.result.data.imageBase64").isEqualTo(PNG_BASE64)
                .jsonPath("$.result.data.mimeType").isEqualTo("image/png")
                .jsonPath("$.result.data.expiresInSeconds").isEqualTo(300)
                .jsonPath("$.messages").isArray()
                .jsonPath("$.errors").isArray()
                .returnResult();

        byte[] png = Base64.getDecoder().decode(PNG_BASE64);
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(new String(exchange.getResponseBodyContent()))
                .doesNotContain(
                        CAPTCHA_ANSWER,
                        "answerHash",
                        "captchaAnswer",
                        "auth_flow_sid",
                        "Account-Ref");

        CaptchaChallenge persisted = challengePort.require(new CaptchaChallengeId(CHALLENGE_ID));
        assertThat(persisted.maxAttempts()).isEqualTo(5);
        assertThat(persisted.answerHash().value()).isNotBlank();
        assertThat(persisted.toString()).doesNotContain(CAPTCHA_ANSWER, PNG_BASE64);
    }

    @Test
    void catalogueContainsEightyTwoUniqueCases() throws Exception {
        RegistrationTestCatalog catalog = RegistrationTestCatalog.loadDefault();
        assertThat(catalog.rows()).hasSize(82);
        assertThat(catalog.testIds()).hasSize(82).contains("TC-001", "TC-082");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
    })
    @EnableConfigurationProperties(CorsProperties.class)
    @Import({
            SecurityConfig.class,
            AuthCaptchaController.class
    })
    static class TestApplication {

        @Bean
        Clock registrationIntegrationClock() {
            return Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        AuthWorkflowPolicyPort authWorkflowPolicyPort() {
            return new AuthWorkflowPolicyPort() {
                @Override
                public RegistrationAuthWorkflowPolicy registrationPolicy() {
                    return new RegistrationAuthWorkflowPolicy(
                            null,
                            new CaptchaPolicy(8, "ABC123", false, 300, 5),
                            null,
                            null,
                            null);
                }

                @Override
                public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
                    return Optional.empty();
                }
            };
        }

        @Bean
        CaptchaImageGeneratorPort captchaImageGeneratorPort() {
            return policy -> Mono.just(new GeneratedCaptcha(
                    CAPTCHA_ANSWER,
                    new CaptchaImage(PNG_BASE64, "image/png")));
        }

        @Bean
        CaptchaAnswerHasherPort captchaAnswerHasherPort() {
            return new CaptchaAnswerHasherPort() {
                @Override
                public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
                    String normalized = caseSensitive ? answer : answer.toUpperCase();
                    return new CaptchaAnswerHash(String.format("%064x", normalized.hashCode() & 0xffffffffL));
                }

                @Override
                public boolean matches(
                        String submittedAnswer,
                        CaptchaAnswerHash expectedHash,
                        boolean caseSensitive) {
                    return hash(submittedAnswer, caseSensitive).equals(expectedHash);
                }
            };
        }

        @Bean
        InMemoryCaptchaChallengePort captchaChallengePort() {
            return new InMemoryCaptchaChallengePort();
        }

        @Bean
        CaptchaChallengeIdGenerator captchaChallengeIdGenerator() {
            return () -> new CaptchaChallengeId(CHALLENGE_ID);
        }

        @Bean
        CreateCaptchaChallengeUseCase createCaptchaChallengeUseCase(
                AuthWorkflowPolicyPort policyPort,
                CaptchaImageGeneratorPort imageGeneratorPort,
                CaptchaAnswerHasherPort hasherPort,
                CaptchaChallengePort challengePort,
                CaptchaChallengeIdGenerator idGenerator,
                Clock clock) {
            return new CreateCaptchaChallengeService(
                    policyPort,
                    imageGeneratorPort,
                    hasherPort,
                    challengePort,
                    idGenerator,
                    clock);
        }

        @Bean
        VerifyCaptchaUseCase verifyCaptchaUseCase() {
            return command -> Mono.error(new AssertionError(
                    "captcha verify is introduced by subtask 002 runtime migration"));
        }

        @Bean
        RefreshCaptchaUseCase refreshCaptchaUseCase() {
            return command -> Mono.error(new AssertionError(
                    "captcha refresh is introduced by subtask 002 runtime migration"));
        }

        @Bean
        CaptchaWebMapper captchaWebMapper() {
            return new CaptchaWebMapper(
                    (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code);
        }
    }

    static final class InMemoryCaptchaChallengePort implements CaptchaChallengePort {
        private final Map<CaptchaChallengeId, CaptchaChallenge> challenges =
                new ConcurrentHashMap<>();

        @Override
        public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
            challenges.put(challenge.captchaChallengeId(), challenge);
            return Mono.just(challenge);
        }

        @Override
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
            return Mono.just(Optional.ofNullable(challenges.get(captchaChallengeId)));
        }

        @Override
        public Mono<CaptchaChallenge> markVerified(
                CaptchaChallengeId captchaChallengeId,
                Instant verifiedAt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<CaptchaChallenge> incrementFailedAttempt(
                CaptchaChallengeId captchaChallengeId,
                Instant attemptedAt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<CaptchaChallenge> markLocked(
                CaptchaChallengeId captchaChallengeId,
                Instant lockedAt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<CaptchaChallenge> markConsumed(
                CaptchaChallengeId captchaChallengeId,
                Instant consumedAt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<CaptchaChallenge> markRefreshed(
                CaptchaChallengeId captchaChallengeId,
                Instant refreshedAt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
            challenges.remove(captchaChallengeId);
            return Mono.empty();
        }

        CaptchaChallenge require(CaptchaChallengeId captchaChallengeId) {
            CaptchaChallenge challenge = challenges.get(captchaChallengeId);
            if (challenge == null) {
                throw new AssertionError("Captcha challenge was not persisted");
            }
            return challenge;
        }
    }
}
