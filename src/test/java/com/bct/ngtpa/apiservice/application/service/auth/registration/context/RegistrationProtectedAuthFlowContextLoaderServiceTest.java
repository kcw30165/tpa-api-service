package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegistrationProtectedAuthFlowContextLoaderServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC);
    private final FakeAuthFlowSessionPort port = new FakeAuthFlowSessionPort();
    private final AuthFlowSessionLifecycleService lifecycleService = new AuthFlowSessionLifecycleService(port, new FixedSessionIdGenerator(), new SessionTtlCalculator(), CLOCK);
    private final AuthFlowSessionCsrfService csrfService = new AuthFlowSessionCsrfService(port, new FixedCsrfTokenGenerator(), new Sha256CsrfTokenHasher());
    private final FakeContextProtector protector = new FakeContextProtector();
    private final DefaultRegistrationProtectedAuthFlowContextLoader loader = new DefaultRegistrationProtectedAuthFlowContextLoader(lifecycleService, csrfService, protector, CLOCK);

    @Test
    void loadValidatesCsrfLoadsSessionAndDecryptsRegistrationContext() {
        StepVerifier.create(csrfService.createForSession(new CreateAuthFlowSessionCsrfCommand(new AuthFlowSessionId("afs_abc"), CLOCK.instant(), 300, "corr-1")))
                .assertNext(result -> assertThat(result.rawToken()).isEqualTo("csrf-token"))
                .verifyComplete();

        StepVerifier.create(loader.load(new RegistrationProtectedAuthFlowContextCommand(new AuthFlowSessionId("afs_abc"), "csrf-token", null, null, "corr")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(RegistrationProtectedAuthFlowContextStatus.VALID);
                    assertThat(result.activeOtpChallengeId()).isEqualTo("otp_abc");
                    assertThat(result.maskedDestination()).isEqualTo("m****r@example.com");
                    assertThat(result.registrationContext().identityType()).isEqualTo(RegistrationIdentityType.PASSPORT);
                    assertThat(result.toString()).doesNotContain("3024102", "member@example.com", "otp_abc");
                })
                .verifyComplete();
    }

    @Test
    void invalidCsrfDoesNotDecryptContext() {
        StepVerifier.create(loader.load(new RegistrationProtectedAuthFlowContextCommand(new AuthFlowSessionId("afs_abc"), "bad-token", null, null, "corr")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID))
                .verifyComplete();
        assertThat(protector.unprotectCalled).isFalse();
    }

    private static AuthFlowSession session(String csrfTokenHash) {
        return new AuthFlowSession(
                new AuthFlowSessionId("afs_abc"),
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.OTP_SENT,
                csrfTokenHash,
                "otp_abc",
                new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of(
                        RegistrationAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT, "ciphertext",
                        RegistrationAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION, "v1",
                        RegistrationAuthFlowContextAttributeNames.CONTEXT_ALGORITHM, RegistrationAuthFlowEncryptedContext.DEFAULT_ALGORITHM)),
                CLOCK.instant(),
                CLOCK.instant(),
                CLOCK.instant().plusSeconds(300));
    }

    private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
        AuthFlowSession session = session(null);
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) { session = new AuthFlowSession(command.authFlowSessionId(), command.scenario(), command.initialStatus(), command.csrfTokenHash(), command.activeOtpChallengeId(), command.data(), command.createdAt(), command.createdAt(), command.expiresAt()); return Mono.just(session); }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) { return Mono.just(Optional.ofNullable(session)); }
        @Override public Mono<AuthFlowSession> update(AuthFlowSession session) { this.session = session; return Mono.just(session); }
        @Override public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) { return Mono.just(session); }
        @Override public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) { return Mono.just(session); }
        @Override public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) { return Mono.just(session); }
        @Override public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) { session = null; return Mono.empty(); }
    }
    private static final class FixedSessionIdGenerator implements SessionIdGenerator { @Override public com.bct.ngtpa.apiservice.application.dto.session.SessionId generate(SessionIdGenerationCommand command) { return new com.bct.ngtpa.apiservice.application.dto.session.SessionId("afs_abc"); } }
    private static final class FixedCsrfTokenGenerator implements CsrfTokenGenerator { @Override public String generate() { return "csrf-token"; } }
    private static final class FakeContextProtector implements RegistrationAuthFlowContextProtectorPort {
        boolean unprotectCalled;
        @Override public RegistrationAuthFlowEncryptedContext protect(RegistrationAuthFlowContext context) { return new RegistrationAuthFlowEncryptedContext("ciphertext", "v1", RegistrationAuthFlowEncryptedContext.DEFAULT_ALGORITHM); }
        @Override public RegistrationAuthFlowContext unprotect(RegistrationAuthFlowEncryptedContext encryptedContext) { unprotectCalled = true; return new RegistrationAuthFlowContext(RegistrationIdentityType.PASSPORT, "3024102", "04/04/1982", RegistrationContactType.EMAIL, "member@example.com"); }
    }
}
