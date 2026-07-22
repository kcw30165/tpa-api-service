package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestHeaderContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequiredRequestIdWebFilter;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
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
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.RefreshCaptchaService;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.VerifyCaptchaService;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RegistrationCaptchaAndStartIntegrationTest.TestApplication.class,
        properties = {
                "api.security.require-authentication=false",
                "redis-cache.enabled=false",
                "spring.main.banner-mode=off"
        })
@AutoConfigureWebTestClient
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationCaptchaAndStartIntegrationTest {
    private static final String CAPTCHA_BASE = "/api/v1/auth/captcha";
    private static final String ANSWER = "AbC12312";
    private static final String WRONG = "Wrong999";
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=";

    @Autowired WebTestClient client;
    @Autowired InMemoryCaptchaPort store;
    @Autowired MutableClock clock;
    @Autowired SequenceCaptchaIdGenerator ids;
    private final AtomicInteger requestIds = new AtomicInteger();

    @BeforeEach
    void reset() {
        store.clear();
        clock.reset();
        ids.reset();
        requestIds.set(0);
    }

    @Test
    @RegistrationTestCase("TC-002")
    void requestIdIsEchoed() {
        client.post().uri(CAPTCHA_BASE + "/challenge")
                .header("X-Request-Id", "0041-tc-002")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "0041-tc-002");
    }
    @Test
    @RegistrationTestCase("TC-003")
    void missingRequestIdIsRejectedBeforeCaptchaCreation() {
        int before = store.size();

        client.post()
                .uri(CAPTCHA_BASE + "/challenge")
                .header("Accept-Language", "en")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].code").isEqualTo("err.request.id.missing")
                .jsonPath("$.errors[0].targets[0]").isEqualTo("X-Request-Id");

        assertThat(store.size()).isEqualTo(before);
    }

    @Test
    @RegistrationTestCase("TC-004")
    void unsupportedLanguageFallsBackWithoutBilingualPayload() {
        client.post().uri(CAPTCHA_BASE + "/challenge?lang=zh_HK")
                .header("X-Request-Id", requestId("004"))
                .header("Accept-Language", "unsupported-language")
                .exchange().expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"status\":\"SUCCESS\"")
                    .contains("\"nextStep\":\"NONE\"")
                    .doesNotContain("pageRef"));
    }

    @Test
    @RegistrationTestCase("TC-005")
    void captchaChallengeNeedsNoRegistrationCredentials() {
        client.post().uri(CAPTCHA_BASE + "/challenge")
                .header("X-Request-Id", requestId("005"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.success").isEqualTo(true);
    }

    @Test
    @RegistrationTestCase("TC-006")
    void correctAnswerPersistsVerifiedStateWithoutPlaintext() {
        String id = createChallenge();
        verifyOk(id, ANSWER).jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.result.nextStep").isEqualTo("NONE")
            .jsonPath("$.result.data.verified").isEqualTo(true);
        CaptchaChallenge persisted = store.require(id);
        assertThat(persisted.status()).isEqualTo(CaptchaStatus.VERIFIED);
        assertThat(persisted.toString()).doesNotContain(ANSWER);
    }

    @Test
    @RegistrationTestCase("TC-007")
    void wrongAnswerDecrementsAttempts() {
        String id = createChallenge();
        verifyFailure(id, WRONG).jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.result.nextStep").isEqualTo("NONE")
            .jsonPath("$.result.data.remainingAttempts").isEqualTo(4)
                .jsonPath("$.errors[0].code").isEqualTo("err.registration.captcha.invalid");
        assertThat(store.require(id).failedAttemptCount()).isEqualTo(1);
    }

    @Test
    @RegistrationTestCase("TC-008")
    void fifthWrongAnswerLocksChallengeAndLaterAttemptRemainsLocked() {
        String id = createChallenge();
        for (int attempt = 1; attempt < 5; attempt++) {
            verifyFailure(id, WRONG).jsonPath("$.result.data.remainingAttempts").isEqualTo(5 - attempt);
        }
        verifyFailure(id, WRONG).jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.locked");
        verifyFailure(id, ANSWER).jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.locked");
        assertThat(store.require(id).status()).isEqualTo(CaptchaStatus.LOCKED);
    }

    @Test
    @RegistrationTestCase("TC-009")
    void challengeExpiresAfterConfiguredTtl() {
        String id = createChallenge();
        clock.advance(Duration.ofSeconds(300));
        verifyFailure(id, ANSWER).jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.expired");
        assertThat(store.require(id).status()).isEqualTo(CaptchaStatus.CREATED);
    }

    @Test
    @RegistrationTestCase("TC-010")
    void unknownChallengeReturnsSafeNotFound() {
        verifyFailure("cap_unknown000000000", ANSWER)
                .jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.notFound");
    }

    @Test
    @RegistrationTestCase("TC-011")
    void refreshInvalidatesPreviousChallengeAndCreatesNewChallenge() {
        String oldId = createChallenge();
        String newId = client.post().uri(CAPTCHA_BASE + "/refresh")
                .header("X-Request-Id", requestId("011"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", oldId))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.success").isEqualTo(true)
                .returnResult().getResponseBodyContent() == null
                ? "" : store.latestIdExcluding(oldId);
        assertThat(newId).isNotBlank().isNotEqualTo(oldId);
        assertThat(store.require(oldId).status()).isEqualTo(CaptchaStatus.REFRESHED);
        assertThat(store.require(newId).status()).isEqualTo(CaptchaStatus.CREATED);
    }

    @Test
    @RegistrationTestCase("TC-012")
    void refreshOfUnknownChallengeStillCreatesIndependentNewChallenge() {
        client.post().uri(CAPTCHA_BASE + "/refresh")
                .header("X-Request-Id", requestId("012"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", "cap_unknown000000000"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.success").isEqualTo(true);
        assertThat(store.size()).isEqualTo(1);
    }

    private String requestId(String scenario) {
        return "0041-captcha-" + scenario + "-" + ids.currentSequence();
    }

    private String createChallenge() {
        client.post().uri(CAPTCHA_BASE + "/challenge")
                .header("X-Request-Id", requestId("challenge"))
                .exchange().expectStatus().isOk();
        return store.latestId();
    }

    private WebTestClient.BodyContentSpec verifyOk(String id, String answer) {
        return verifyRequest(id, answer).expectStatus().isOk().expectBody();
    }

    private WebTestClient.BodyContentSpec verifyFailure(String id, String answer) {
        return verifyRequest(id, answer).expectStatus().isBadRequest().expectBody();
    }

    private WebTestClient.ResponseSpec verifyRequest(String id, String answer) {
        return client.post().uri(CAPTCHA_BASE + "/verify")
                .header("X-Request-Id", requestId("verify"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", id, "answer", answer))
                .exchange();
    }


    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
    })
    @EnableConfigurationProperties(CorsProperties.class)
    @Import({SecurityConfig.class, AuthCaptchaController.class})
    static class TestApplication {
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean SequenceCaptchaIdGenerator ids() { return new SequenceCaptchaIdGenerator(); }
        @Bean InMemoryCaptchaPort captchaPort() { return new InMemoryCaptchaPort(); }
        @Bean AuthWorkflowPolicyPort policy() {
            return new AuthWorkflowPolicyPort() {
                public RegistrationAuthWorkflowPolicy registrationPolicy() {
                    return new RegistrationAuthWorkflowPolicy(
                            null, new CaptchaPolicy(8, "ABC123", false, 300, 5), null, null, null);
                }
                public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
                    return Optional.empty();
                }
            };
        }
        @Bean CaptchaImageGeneratorPort imageGenerator() {
            return policy -> Mono.just(new GeneratedCaptcha(ANSWER, new CaptchaImage(PNG, "image/png")));
        }
        @Bean CaptchaAnswerHasherPort hasher() { return new DeterministicHasher(); }
        @Bean CreateCaptchaChallengeUseCase create(
                AuthWorkflowPolicyPort p, CaptchaImageGeneratorPort i, CaptchaAnswerHasherPort h,
                CaptchaChallengePort s, CaptchaChallengeIdGenerator g, Clock c) {
            return new CreateCaptchaChallengeService(p, i, h, s, g, c);
        }
        @Bean VerifyCaptchaUseCase verifyFailure(
                AuthWorkflowPolicyPort p, CaptchaAnswerHasherPort h, CaptchaChallengePort s, Clock c) {
            return new VerifyCaptchaService(p, h, s, c);
        }
        @Bean RefreshCaptchaUseCase refresh(
                AuthWorkflowPolicyPort p, CaptchaImageGeneratorPort i, CaptchaAnswerHasherPort h,
                CaptchaChallengePort s, CaptchaChallengeIdGenerator g, Clock c) {
            return new RefreshCaptchaService(p, i, h, s, g, c);
        }
        @Bean CaptchaWebMapper mapper() {
            return new CaptchaWebMapper((code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code);
        }
        @Bean com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver errorMessageResolver() {
            return (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code;
        }
        @Bean RequestLoggingProperties requestLoggingProperties() {
            RequestLoggingProperties properties = new RequestLoggingProperties();
            properties.setEnabled(false);
            return properties;
        }
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
        @Bean LoggingSanitizerProperties loggingSanitizerProperties() {
            return new LoggingSanitizerProperties();
        }
        @Bean LoggingSanitizer loggingSanitizer(
                ObjectMapper objectMapper,
                LoggingSanitizerProperties properties) {
            return new LoggingSanitizer(objectMapper, properties);
        }
        @Bean RequestLoggingWebFilter requestLoggingWebFilter(
                RequestLoggingProperties properties,
                LoggingSanitizer sanitizer,
                ObjectMapper objectMapper) {
            return new RequestLoggingWebFilter(properties, sanitizer, objectMapper);
        }
        @Bean RequestHeaderContextWebFilter requestHeaderContextWebFilter(
                RequestLoggingWebFilter requestLoggingWebFilter) {
            return new RequestHeaderContextWebFilter(requestLoggingWebFilter);
        }
        @Bean RequiredRequestIdWebFilter requiredRequestIdWebFilter(
                com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver resolver,
                ObjectMapper objectMapper) {
            return new RequiredRequestIdWebFilter(objectMapper);
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-17T00:00:00Z");
        void reset() { instant = Instant.parse("2026-07-17T00:00:00Z"); }
        void advance(Duration duration) { instant = instant.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
    }

    static final class SequenceCaptchaIdGenerator implements CaptchaChallengeIdGenerator {
        private final AtomicInteger sequence = new AtomicInteger();
        void reset() { sequence.set(0); }
        int currentSequence() { return sequence.get(); }
        public CaptchaChallengeId nextId() {
            return new CaptchaChallengeId("cap_" + String.format("%016d", sequence.incrementAndGet()));
        }
    }

    static final class DeterministicHasher implements CaptchaAnswerHasherPort {
        public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
            String normalized = caseSensitive ? answer : answer.toUpperCase();
            return new CaptchaAnswerHash(String.format("%064x", normalized.hashCode() & 0xffffffffL));
        }
        public boolean matches(String answer, CaptchaAnswerHash hash, boolean caseSensitive) {
            return hash(answer, caseSensitive).equals(hash);
        }
    }

    static final class InMemoryCaptchaPort implements CaptchaChallengePort {
        private final Map<CaptchaChallengeId, CaptchaChallenge> values = new ConcurrentHashMap<>();
        void clear() { values.clear(); }
        int size() { return values.size(); }
        String latestId() { return values.keySet().stream().map(CaptchaChallengeId::value).sorted().reduce((a,b)->b).orElseThrow(); }
        String latestIdExcluding(String excluded) { return values.keySet().stream().map(CaptchaChallengeId::value).filter(id -> !id.equals(excluded)).sorted().reduce((a,b)->b).orElseThrow(); }
        CaptchaChallenge require(String id) { return values.get(new CaptchaChallengeId(id)); }
        public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) { values.put(challenge.captchaChallengeId(), challenge); return Mono.just(challenge); }
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId id) { return Mono.just(Optional.ofNullable(values.get(id))); }
        public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId id, Instant at) { return update(id, old -> copy(old, CaptchaStatus.VERIFIED, old.failedAttemptCount(), at, null, null, null)); }
        public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId id, Instant at) { return update(id, old -> copy(old, old.status(), old.failedAttemptCount()+1, old.verifiedAt(), old.consumedAt(), old.refreshedAt(), old.lockedAt())); }
        public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId id, Instant at) { return update(id, old -> copy(old, CaptchaStatus.LOCKED, old.failedAttemptCount(), old.verifiedAt(), old.consumedAt(), old.refreshedAt(), at)); }
        public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId id, Instant at) { return update(id, old -> copy(old, CaptchaStatus.CONSUMED, old.failedAttemptCount(), old.verifiedAt(), at, old.refreshedAt(), old.lockedAt())); }
        public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId id, Instant at) { return update(id, old -> copy(old, CaptchaStatus.REFRESHED, old.failedAttemptCount(), old.verifiedAt(), old.consumedAt(), at, old.lockedAt())); }
        public Mono<Void> delete(CaptchaChallengeId id) { values.remove(id); return Mono.empty(); }
        private Mono<CaptchaChallenge> update(CaptchaChallengeId id, java.util.function.Function<CaptchaChallenge,CaptchaChallenge> fn) { CaptchaChallenge next=fn.apply(values.get(id)); values.put(id,next); return Mono.just(next); }
        private CaptchaChallenge copy(CaptchaChallenge old, CaptchaStatus status, int failures, Instant verified, Instant consumed, Instant refreshed, Instant locked) {
            return new CaptchaChallenge(old.captchaChallengeId(), old.answerHash(), status, failures, old.maxAttempts(), old.createdAt(), old.expiresAt(), verified, consumed, refreshed, locked);
        }
    }
}
