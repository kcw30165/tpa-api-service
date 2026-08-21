package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionCsrfServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
  private final FakeAuthFlowSessionPort port = new FakeAuthFlowSessionPort();
  private final AuthFlowSessionCsrfService service =
      new AuthFlowSessionCsrfService(
          port, new FixedCsrfTokenGenerator("csrf_token_a"), new Sha256CsrfTokenHasher());

  @Test
  void createCsrfTokenStoresHashInSessionAndReturnsRawToken() {
    port.save(activeSession(null, NOW.plusSeconds(1800)));

    StepVerifier.create(
            service.createForSession(
                new CreateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_abc"), NOW, 1800, "corr-1")))
        .assertNext(
            result -> {
              assertThat(result.rawToken()).isEqualTo("csrf_token_a");
              assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
              assertThat(result.expiresInSeconds()).isEqualTo(1800);
            })
        .verifyComplete();

    AuthFlowSession updated = port.sessions.get("afs_abc");
    assertThat(updated.csrfTokenHash()).isNotBlank();
    assertThat(updated.csrfTokenHash()).isNotEqualTo("csrf_token_a");
    assertThat(updated.updatedAt()).isEqualTo(NOW);
    assertThat(updated.toString()).doesNotContain("csrf_token_a");
    assertThat(port.updateCount).isEqualTo(1);
  }

  @Test
  void createCsrfTokenRejectsMissingSession() {
    StepVerifier.create(
            service.createForSession(
                new CreateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_missing"), NOW, 1800, null)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Auth flow session not available"))
        .verify();
    assertThat(port.updateCount).isZero();
  }

  @Test
  void createCsrfTokenRejectsExpiredSession() {
    port.save(activeSession(null, NOW));

    StepVerifier.create(
            service.createForSession(
                new CreateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_abc"), NOW, 1800, null)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Auth flow session is not usable"))
        .verify();
    assertThat(port.sessions.get("afs_abc").csrfTokenHash()).isNull();
    assertThat(port.updateCount).isZero();
  }

  @Test
  void createCsrfTokenRejectsTerminalSession() {
    for (AuthFlowStatus status :
        new AuthFlowStatus[] {
          AuthFlowStatus.COMPLETED, AuthFlowStatus.CANCELLED, AuthFlowStatus.EXPIRED
        }) {
      port.clear();
      port.save(session(status, null, NOW.plusSeconds(1800)));
      StepVerifier.create(
              service.createForSession(
                  new CreateAuthFlowSessionCsrfCommand(
                      new AuthFlowSessionId("afs_abc"), NOW, 1800, null)))
          .expectErrorSatisfies(
              error ->
                  assertThat(error)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessage("Auth flow session is not usable"))
          .verify();
      assertThat(port.updateCount).isZero();
    }
  }

  @Test
  void validateForSessionLoadsOnceAndValidatesMatchingToken() {
    String hash = new Sha256CsrfTokenHasher().hash("csrf_token_a");
    port.save(activeSession(hash, NOW.plusSeconds(1800)));
    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNextMatches(ValidateAuthFlowSessionCsrfResult::valid)
        .verifyComplete();
    assertThat(port.loadCount).isOne();
  }

  @Test
  void validateCsrfReturnsValidForMatchingToken() {
    String hash = new Sha256CsrfTokenHasher().hash("csrf_token_a");
    port.save(activeSession(hash, NOW.plusSeconds(1800)));

    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(AuthFlowSessionCsrfValidationStatus.VALID, true))
        .verifyComplete();
  }

  @Test
  void validateCsrfReturnsSessionMissing() {
    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(
                AuthFlowSessionCsrfValidationStatus.SESSION_MISSING, false))
        .verifyComplete();
  }

  @Test
  void validateCsrfReturnsSessionExpired() {
    port.save(activeSession(new Sha256CsrfTokenHasher().hash("csrf_token_a"), NOW));

    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(
                AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED, false))
        .verifyComplete();
  }

  @Test
  void validateCsrfReturnsSessionNotUsableForTerminalStatus() {
    String hash = new Sha256CsrfTokenHasher().hash("csrf_token_a");
    for (AuthFlowStatus status :
        new AuthFlowStatus[] {
          AuthFlowStatus.COMPLETED, AuthFlowStatus.CANCELLED, AuthFlowStatus.EXPIRED
        }) {
      port.clear();
      port.save(session(status, hash, NOW.plusSeconds(1800)));
      StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
          .expectNext(
              new ValidateAuthFlowSessionCsrfResult(
                  AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE, false))
          .verifyComplete();
    }
  }

  @Test
  void validateCsrfReturnsTokenMissing() {
    port.save(
        activeSession(new Sha256CsrfTokenHasher().hash("csrf_token_a"), NOW.plusSeconds(1800)));

    StepVerifier.create(service.validateForSession(validateCommand(" ", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(
                AuthFlowSessionCsrfValidationStatus.TOKEN_MISSING, false))
        .verifyComplete();
  }

  @Test
  void validateCsrfReturnsTokenHashMissing() {
    port.save(activeSession(null, NOW.plusSeconds(1800)));

    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(
                AuthFlowSessionCsrfValidationStatus.TOKEN_HASH_MISSING, false))
        .verifyComplete();
  }

  @Test
  void validateCsrfReturnsMismatchForWrongToken() {
    port.save(
        activeSession(new Sha256CsrfTokenHasher().hash("csrf_token_a"), NOW.plusSeconds(1800)));

    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_b", NOW)))
        .expectNext(
            new ValidateAuthFlowSessionCsrfResult(
                AuthFlowSessionCsrfValidationStatus.TOKEN_MISMATCH, false))
        .verifyComplete();
  }

  @Test
  void validateCsrfDoesNotMutateSession() {
    port.save(
        activeSession(new Sha256CsrfTokenHasher().hash("csrf_token_a"), NOW.plusSeconds(1800)));

    StepVerifier.create(service.validateForSession(validateCommand("csrf_token_a", NOW)))
        .expectNextMatches(ValidateAuthFlowSessionCsrfResult::valid)
        .verifyComplete();
    assertThat(port.updateCount).isZero();
  }

  @Test
  void rotateReportsOriginalExpiryAndRemainingLifetimeInsteadOfResettingTtl() {
    port.save(activeSession(new Sha256CsrfTokenHasher().hash("old_token"), NOW.plusSeconds(90)));
    var rotatingService =
        new AuthFlowSessionCsrfService(
            port, new FixedCsrfTokenGenerator("new_token"), new Sha256CsrfTokenHasher());

    StepVerifier.create(
            rotatingService.rotateForSession(
                new RotateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_abc"), NOW, 1800, "corr-remaining")))
        .assertNext(
            result -> {
              assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(90));
              assertThat(result.expiresInSeconds()).isEqualTo(90);
            })
        .verifyComplete();
    assertThat(port.sessions.get("afs_abc").expiresAt()).isEqualTo(NOW.plusSeconds(90));
  }

  @Test
  void rotateCsrfTokenReplacesStoredHashAndReturnsNewRawToken() {
    var oldHash = new Sha256CsrfTokenHasher().hash("old_token");
    port.save(activeSession(oldHash, NOW.plusSeconds(1800)));
    var rotatingService =
        new AuthFlowSessionCsrfService(
            port, new FixedCsrfTokenGenerator("new_token"), new Sha256CsrfTokenHasher());

    StepVerifier.create(
            rotatingService.rotateForSession(
                new RotateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_abc"), NOW, 1800, null)))
        .assertNext(result -> assertThat(result.rawToken()).isEqualTo("new_token"))
        .verifyComplete();
    assertThat(port.sessions.get("afs_abc").csrfTokenHash()).isNotEqualTo(oldHash);
    assertThat(port.sessions.get("afs_abc").updatedAt()).isEqualTo(NOW);
  }

  private static ValidateAuthFlowSessionCsrfCommand validateCommand(String token, Instant now) {
    return new ValidateAuthFlowSessionCsrfCommand(
        new AuthFlowSessionId("afs_abc"), token, now, null, null, java.util.List.of(), null);
  }

  private static AuthFlowSession activeSession(String hash, Instant expiresAt) {
    return session(AuthFlowStatus.OTP_SENT, hash, expiresAt);
  }

  private static AuthFlowSession session(AuthFlowStatus status, String hash, Instant expiresAt) {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_abc"),
        AuthFlowScenario.REGISTRATION,
        status,
        hash,
        "otp_abc",
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private static final class FixedCsrfTokenGenerator implements CsrfTokenGenerator {
    private final String token;

    FixedCsrfTokenGenerator(String token) {
      this.token = token;
    }

    @Override
    public String generate() {
      return token;
    }
  }

  private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
    private final Map<String, AuthFlowSession> sessions = new LinkedHashMap<>();
    private int updateCount;
    private int loadCount;

    void save(AuthFlowSession session) {
      sessions.put(session.authFlowSessionId().value(), session);
    }

    void clear() {
      sessions.clear();
      updateCount = 0;
      loadCount = 0;
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      loadCount++;
      return Mono.just(Optional.ofNullable(sessions.get(id.value())));
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
      updateCount++;
      save(session);
      return Mono.just(session);
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId id) {
      sessions.remove(id.value());
      return Mono.empty();
    }
  }
}
