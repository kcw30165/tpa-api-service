package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoff;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoffStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginReplaySessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RecoverCommittedLoginByHandoffServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
  private static final AuthenticatedLoginHandoffReference HANDOFF =
      new AuthenticatedLoginHandoffReference("0123456789abcdef0123456789abcdef");
  private static final LoginOtpVerificationOperationReference OPERATION =
      new LoginOtpVerificationOperationReference("abcdef0123456789abcdef0123456789");
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void directHandoffRecoveryUsesExistingSessionAndRotatesCsrf() {
    Fixture fixture = new Fixture(cleaned(NOW.plusSeconds(300)), usableSession());

    StepVerifier.create(
            fixture.service.recover(new RecoverCommittedLoginByHandoffCommand(HANDOFF, "req")))
        .assertNext(
            result -> {
              assertThat(result.recovered()).isTrue();
              assertThat(result.authenticatedSessionId()).isEqualTo(SESSION_ID);
              assertThat(result.authenticatedCsrfToken()).isEqualTo("fresh-csrf");
              assertThat(result.accounts()).hasSize(1);
            })
        .verifyComplete();

    verify(fixture.replaySessions).loadForReplay(any());
    verify(fixture.replaySessions).rotateCsrfHashIfCurrent(any());
  }

  @Test
  void directHandoffRecoveryRejectsNonCleanedExpiredMissingAndUnusableState() {
    assertFailed(
        handoff(AuthenticatedLoginHandoffStatus.RESERVED, NOW.plusSeconds(300), null),
        usableSession());
    assertFailed(cleaned(NOW), usableSession());
    assertFailed(null, usableSession());
    assertFailed(cleaned(NOW.plusSeconds(300)), unusableSession());
  }

  @Test
  void directHandoffRecoveryRejectsSessionIdentityMismatch() {
    AuthenticatedSession mismatched =
        new AuthenticatedSession(
            new AuthenticatedSessionId("sid_differentabcdefghijklmnop"),
            new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
            List.of(account()),
            AuthenticatedSessionStatus.ACTIVE,
            "old-csrf-hash",
            NOW.minusSeconds(60),
            NOW.minusSeconds(30),
            NOW.plusSeconds(300),
            NOW.plusSeconds(600),
            new SessionRevision("0".repeat(32)),
            null);

    assertFailed(cleaned(NOW.plusSeconds(300)), mismatched);
  }

  @Test
  void directHandoffRecoveryRejectsCsrfRotationConflict() {
    Fixture fixture = new Fixture(cleaned(NOW.plusSeconds(300)), usableSession());
    when(fixture.replaySessions.rotateCsrfHashIfCurrent(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionCsrfRotationResult(
                    AuthenticatedSessionCsrfRotationStatus.CONFLICT)));

    StepVerifier.create(
            fixture.service.recover(new RecoverCommittedLoginByHandoffCommand(HANDOFF, "req")))
        .expectNext(RecoverCommittedLoginResult.failed())
        .verifyComplete();
  }

  @Test
  void proofBasedRecoveryRemainsAvailableAndChecksAuthenticIdentity() {
    Fixture fixture = new Fixture(cleaned(NOW.plusSeconds(300)), usableSession());
    var proof =
        new LoginOtpConsumptionProof(
            "reservation-hash",
            HANDOFF,
            OPERATION,
            "authentic-challenge-fingerprint",
            NOW.minusSeconds(1));

    StepVerifier.create(fixture.service.recover(new RecoverCommittedLoginCommand(proof, "req")))
        .assertNext(result -> assertThat(result.recovered()).isTrue())
        .verifyComplete();
  }

  @Test
  void proofBasedRecoveryRejectsConflictingIdentityBeforeSessionLoad() {
    Fixture fixture = new Fixture(cleaned(NOW.plusSeconds(300)), usableSession());
    var conflicting =
        new LoginOtpConsumptionProof(
            "different-reservation-hash",
            HANDOFF,
            OPERATION,
            "authentic-challenge-fingerprint",
            NOW.minusSeconds(1));

    StepVerifier.create(
            fixture.service.recover(new RecoverCommittedLoginCommand(conflicting, "req")))
        .expectNext(RecoverCommittedLoginResult.failed())
        .verifyComplete();
    verify(fixture.replaySessions, never()).loadForReplay(any());
  }

  private void assertFailed(AuthenticatedLoginHandoff handoff, AuthenticatedSession session) {
    Fixture fixture = new Fixture(handoff, session);
    StepVerifier.create(
            fixture.service.recover(new RecoverCommittedLoginByHandoffCommand(HANDOFF, "req")))
        .expectNext(RecoverCommittedLoginResult.failed())
        .verifyComplete();
  }

  private static AuthenticatedLoginHandoff cleaned(Instant expiresAt) {
    return handoff(AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED, expiresAt, "cleanup-proof");
  }

  private static AuthenticatedLoginHandoff handoff(
      AuthenticatedLoginHandoffStatus status, Instant expiresAt, String cleanupProof) {
    return new AuthenticatedLoginHandoff(
        HANDOFF,
        OPERATION,
        "reservation-hash",
        status,
        status == AuthenticatedLoginHandoffStatus.RESERVED ? null : SESSION_ID,
        cleanupProof,
        NOW.minusSeconds(60),
        expiresAt);
  }

  private static AuthenticatedSessionAccount account() {
    return new AuthenticatedSessionAccount(
        new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
        "DB",
        "00000008802",
        "0001",
        "DBA",
        "OE",
        null,
        null);
  }

  private static AuthenticatedSession usableSession() {
    return new AuthenticatedSession(
        SESSION_ID,
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(account()),
        AuthenticatedSessionStatus.ACTIVE,
        "old-csrf-hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(30),
        NOW.plusSeconds(300),
        NOW.plusSeconds(600),
        new SessionRevision("0".repeat(32)),
        null);
  }

  private static AuthenticatedSession unusableSession() {
    return new AuthenticatedSession(
        SESSION_ID,
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(account()),
        AuthenticatedSessionStatus.ACTIVE,
        "old-csrf-hash",
        NOW.minusSeconds(600),
        NOW.minusSeconds(600),
        NOW.minusSeconds(1),
        NOW.plusSeconds(600),
        new SessionRevision("0".repeat(32)),
        null);
  }

  private static final class Fixture {
    private final AuthenticatedLoginReplaySessionPort replaySessions =
        mock(AuthenticatedLoginReplaySessionPort.class);
    private final RecoverCommittedLoginService service;

    private Fixture(AuthenticatedLoginHandoff handoff, AuthenticatedSession session) {
      AuthenticatedLoginHandoffPort handoffs = mock(AuthenticatedLoginHandoffPort.class);
      AuthenticatedSessionProtectorPort protector = mock(AuthenticatedSessionProtectorPort.class);
      CsrfTokenGenerator generator = mock(CsrfTokenGenerator.class);
      CsrfTokenHasher hasher = mock(CsrfTokenHasher.class);
      ProtectedAuthenticatedSession expected = mock(ProtectedAuthenticatedSession.class);
      ProtectedAuthenticatedSession replacement = mock(ProtectedAuthenticatedSession.class);

      when(handoffs.loadByHandoff(HANDOFF))
          .thenReturn(handoff == null ? Mono.empty() : Mono.just(handoff));
      when(replaySessions.loadForReplay(any()))
          .thenReturn(Mono.just(session == null ? Optional.empty() : Optional.of(expected)));
      when(protector.unprotect(expected)).thenReturn(session);
      when(protector.protect(any(AuthenticatedSession.class))).thenReturn(replacement);
      when(generator.generate()).thenReturn("fresh-csrf");
      when(hasher.hash("fresh-csrf")).thenReturn("fresh-csrf-hash");
      when(replaySessions.rotateCsrfHashIfCurrent(any()))
          .thenReturn(
              Mono.just(
                  new AuthenticatedSessionCsrfRotationResult(
                      AuthenticatedSessionCsrfRotationStatus.ROTATED)));

      service =
          new RecoverCommittedLoginService(
              handoffs,
              replaySessions,
              protector,
              generator,
              hasher,
              Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }
}
