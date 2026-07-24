package com.bct.ngtpa.apiservice.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowConcurrencyDecision;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.LoadAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowDestinationCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowOtpCommand;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class P4BAuthFlowSessionFoundationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

    @Test
    void p4bAuthFlowSessionModelSupportsExpectedScenariosAndStatuses() {
        assertThat(AuthFlowScenario.values()).containsExactly(
                AuthFlowScenario.REGISTRATION,
                AuthFlowScenario.LOGIN,
                AuthFlowScenario.FORGOT_PASSWORD,
                AuthFlowScenario.USERNAME_RECOVERY);
        assertThat(AuthFlowStatus.values()).containsExactly(
                AuthFlowStatus.STARTED,
                AuthFlowStatus.OTP_SENT,
                AuthFlowStatus.OTP_VERIFIED,
                AuthFlowStatus.TRUST_SELECTION_READY,
                AuthFlowStatus.USERNAME_VERIFIED,
                AuthFlowStatus.COMPLETING,
                AuthFlowStatus.COMPLETED,
                AuthFlowStatus.CANCELLED,
                AuthFlowStatus.EXPIRED);
        assertThat(Arrays.stream(AuthFlowStatus.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN", "ERROR");
    }

    @Test
    void p4bAuthFlowSessionIdAndCookieNameRemainSeparate() {
        var id = new AuthFlowSessionId("afs_abc123");
        String cookieName = "auth_flow_sid";
        String redisKey = "ngtpa:auth:flow-session:" + id.value();

        assertThat(id.value()).isEqualTo("afs_abc123").isNotEqualTo(cookieName);
        assertThat(redisKey).isEqualTo("ngtpa:auth:flow-session:afs_abc123").doesNotContain(cookieName);
        assertThat(recordComponentNames(AuthFlowSession.class))
                .doesNotContain("authFlowCookie", "cookieName", "cookieValue");
    }

    @Test
    void p4bCsrfBindingStoresHashOnly() {
        var port = new FakeAuthFlowSessionPort();
        port.save(activeSession(new AuthFlowSessionId("afs_csrf"), AuthFlowStatus.OTP_SENT, null));
        var service = new AuthFlowSessionCsrfService(port, new FixedCsrfTokenGenerator("csrf_raw_secret"), new Sha256CsrfTokenHasher());

        StepVerifier.create(service.createForSession(new CreateAuthFlowSessionCsrfCommand(new AuthFlowSessionId("afs_csrf"), NOW, 1800, null)))
                .assertNext(result -> {
                    assertThat(result.rawToken()).isEqualTo("csrf_raw_secret");
                    assertThat(result.toString()).doesNotContain("csrf_raw_secret");
                })
                .verifyComplete();

        var updated = port.sessions.get("afs_csrf");
        assertThat(updated.csrfTokenHash()).isNotBlank().isNotEqualTo("csrf_raw_secret");
        assertThat(updated.toString()).doesNotContain("csrf_raw_secret");
    }

    @Test
    void p4bLifecycleCreatesSessionWithoutCookieOrHeaderBehavior() {
        var port = new FakeAuthFlowSessionPort();
        var lifecycle = lifecycle(port, new PrefixSessionIdGenerator());

        StepVerifier.create(lifecycle.start(new StartAuthFlowSessionCommand(
                        AuthFlowScenario.REGISTRATION,
                        AuthFlowStatus.STARTED,
                        new SessionTtlPolicy(true, true, 1800),
                        "afs",
                        16,
                        AuthFlowSessionData.empty(),
                        null,
                        null,
                        null)))
                .assertNext(result -> {
                    assertThat(result.session().authFlowSessionId().value()).startsWith("afs_");
                    assertThat(result.session().toString()).doesNotContain("auth_flow_sid", "Cookie", "Header");
                })
                .verifyComplete();
        assertThat(port.cookieInstructionCreated).isFalse();
    }


    @Test
    void p4bConcurrencyReplacesExistingActiveSession() {
        var port = new FakeAuthFlowSessionPort();
        var lifecycle = lifecycle(port, new PrefixSessionIdGenerator());
        var concurrency = new AuthFlowSessionConcurrencyService(lifecycle);
        var existingId = new AuthFlowSessionId("afs_existing");
        port.save(activeSession(existingId, AuthFlowStatus.OTP_SENT, null));

        StepVerifier.create(concurrency.start(new StartAuthFlowSessionWithConcurrencyCommand(
                        AuthFlowScenario.REGISTRATION,
                        AuthFlowStatus.STARTED,
                        new SessionTtlPolicy(true, true, 1800),
                        "afs",
                        16,
                        AuthFlowSessionData.empty(),
                        null,
                        null,
                        existingId,
                        new SessionConcurrencyPolicy(SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING),
                        null)))
                .assertNext(result -> assertThat(result.decision()).isEqualTo(AuthFlowConcurrencyDecision.REPLACED_EXISTING))
                .verifyComplete();
        assertThat(port.sessions.get("afs_existing").status()).isEqualTo(AuthFlowStatus.CANCELLED);
        assertThat(port.cookieInstructionCreated).isFalse();
    }

    @Test
    void p4bConcurrencyRejectsUnsupportedParallelModes() {
        var port = new FakeAuthFlowSessionPort();
        var concurrency = new AuthFlowSessionConcurrencyService(lifecycle(port, new PrefixSessionIdGenerator()));

        StepVerifier.create(concurrency.start(baseConcurrencyCommand(new SessionConcurrencyPolicy(SessionConcurrencyMode.MULTI_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING))))
                .expectError(UnsupportedOperationException.class)
                .verify();
        StepVerifier.create(concurrency.start(baseConcurrencyCommand(new SessionConcurrencyPolicy(SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.ALLOW_PARALLEL))))
                .expectError(UnsupportedOperationException.class)
                .verify();
        assertThat(port.createCount).isZero();
    }

    @Test
    void p4bSensitiveToStringValuesAreRedacted() {
        var session = new AuthFlowSession(
                new AuthFlowSessionId("afs_secret"),
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.OTP_SENT,
                "csrf_hash_secret",
                "otp_secret",
                new AuthFlowSessionData(
                        "EMAIL",
                        "m****r@example.com",
                        Map.of("usernameCheckCiphertext", "encrypted-username-check")),
                NOW,
                NOW,
                NOW.plusSeconds(1800));

        assertThat(session.toString()).doesNotContain("afs_secret", "csrf_hash_secret", "otp_secret", "user_secret", "csrf_raw_secret");
    }

    @Test
    void p4bAuthenticatedSessionPlaceholderPackageOnlyAndReadmeDocumentsFoundationOnly() throws Exception {
        assertThat(Files.exists(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authenticated/package-info.java"))).isTrue();
        assertThat(Class.forName("com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession")).isNotNull();
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter");

        var readme = Files.readString(Path.of("README.md"));
        assertThat(readme).contains("### AuthFlowSession Foundation");
        assertThat(readme).contains("temporary pre-auth workflow session");
        assertThat(readme).contains("not an authenticated portal session");
        assertThat(readme).contains("not proof of login");
        assertThat(readme).contains("registration endpoints");
        assertThat(readme).contains("does not read or write cookies at runtime");
        assertThat(readme).contains("AuthenticatedSession runtime implementation");
    }

    private static StartAuthFlowSessionWithConcurrencyCommand baseConcurrencyCommand(SessionConcurrencyPolicy policy) {
        return new StartAuthFlowSessionWithConcurrencyCommand(
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.STARTED,
                new SessionTtlPolicy(true, true, 1800),
                "afs",
                16,
                AuthFlowSessionData.empty(),
                null,
                null,
                null,
                policy,
                null);
    }

    private static AuthFlowSessionLifecycleService lifecycle(FakeAuthFlowSessionPort port, SessionIdGenerator generator) {
        return new AuthFlowSessionLifecycleService(port, generator, new SessionTtlCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthFlowSession activeSession(AuthFlowSessionId id, AuthFlowStatus status, String csrfHash) {
        return new AuthFlowSession(id, AuthFlowScenario.REGISTRATION, status, csrfHash, null, AuthFlowSessionData.empty(), NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.plusSeconds(1800));
    }

    private static java.util.List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static void assertClassAbsent(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException expected) {
            return;
        }
        throw new AssertionError("Class should be absent: " + className);
    }

    private static final class FixedCsrfTokenGenerator implements CsrfTokenGenerator {
        private final String token;
        private FixedCsrfTokenGenerator(String token) { this.token = token; }
        @Override public String generate() { return token; }
    }

    private static final class PrefixSessionIdGenerator implements SessionIdGenerator {
        private int sequence;
        @Override public SessionId generate(SessionIdGenerationCommand command) {
            sequence++;
            return new SessionId(command.prefix() + "_generated_" + sequence);
        }
    }

    private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
        private final Map<String, AuthFlowSession> sessions = new LinkedHashMap<>();
        private boolean cookieInstructionCreated;
        private int createCount;
        void save(AuthFlowSession session) { sessions.put(session.authFlowSessionId().value(), session); }
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            createCount++;
            var session = new AuthFlowSession(command.authFlowSessionId(), command.scenario(), command.initialStatus(), command.csrfTokenHash(), command.activeOtpChallengeId(), command.data(), command.createdAt(), command.createdAt(), command.expiresAt());
            save(session); return Mono.just(session);
        }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) { return Mono.just(Optional.ofNullable(sessions.get(id.value()))); }
        @Override public Mono<AuthFlowSession> update(AuthFlowSession session) { save(session); return Mono.just(session); }
        @Override public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) { var existing = sessions.get(id.value()); var updated = new AuthFlowSession(existing.authFlowSessionId(), existing.scenario(), AuthFlowStatus.COMPLETED, existing.csrfTokenHash(), existing.activeOtpChallengeId(), existing.data(), existing.createdAt(), now, existing.expiresAt()); save(updated); return Mono.just(updated); }
        @Override public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) { var existing = sessions.get(id.value()); var updated = new AuthFlowSession(existing.authFlowSessionId(), existing.scenario(), AuthFlowStatus.CANCELLED, existing.csrfTokenHash(), existing.activeOtpChallengeId(), existing.data(), existing.createdAt(), now, existing.expiresAt()); save(updated); return Mono.just(updated); }
        @Override public Mono<Void> delete(AuthFlowSessionId id) { sessions.remove(id.value()); return Mono.empty(); }
    }
}
