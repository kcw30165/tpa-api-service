package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallengeInvalidationReason;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CancelForgotPasswordServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
  private static final AuthFlowSessionId SID = new AuthFlowSessionId("afs_fp_cancel");
  private static final List<String> ALLOWED = List.of("https://portal.test");

  @Test
  void activeForgotPasswordFlowCancelsAndInvalidatesOtp() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CANCELLED);
              assertThat(r.successfulCleanup()).isTrue();
              assertThat(r.clearAuthFlowCookie()).isTrue();
              assertThat(r.clearXsrfCookie()).isTrue();
              assertThat(r.errors()).isEmpty();
            })
        .verifyComplete();
    assertThat(f.invalidated).hasValue(1);
    assertThat(f.reason.get())
        .isEqualTo(OtpChallengeInvalidationReason.USER_CANCELLED_FORGOT_PASSWORD);
    assertThat(f.cancelled).hasValue(1);
    assertThat(f.cancelReason.get()).isEqualTo(AuthFlowSessionCancellationReason.USER_CANCELLED);
    assertThat(f.cancelledAt.get()).isEqualTo(NOW);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void activeForgotPasswordFlowWithoutOtpCancelsWithoutOtpInvalidation() {
    var f = fixture(AuthFlowStatus.STARTED, "csrf-safe", null);
    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CANCELLED))
        .verifyComplete();
    assertThat(f.invalidated).hasValue(0);
    assertThat(f.cancelled).hasValue(1);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void missingSessionIsSuccessfulCleanup() {
    var f = new Fixture(null);
    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.SESSION_NOT_FOUND);
              assertThat(r.successfulCleanup()).isTrue();
              assertThat(r.clearAuthFlowCookie()).isTrue();
              assertThat(r.clearXsrfCookie()).isTrue();
              assertThat(r.errors()).isEmpty();
            })
        .verifyComplete();
    assertThat(f.invalidated).hasValue(0);
    assertThat(f.cancelled).hasValue(0);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void alreadyCancelledIsSuccessfulCleanup() {
    var f = fixture(AuthFlowStatus.CANCELLED, "csrf-safe", "otp_active");
    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.ALREADY_CANCELLED);
              assertThat(r.successfulCleanup()).isTrue();
            })
        .verifyComplete();
    assertThat(f.invalidated).hasValue(0);
    assertThat(f.cancelled).hasValue(0);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void expiredOrTerminalIsSuccessfulCleanup() {
    for (AuthFlowStatus status :
        new AuthFlowStatus[] {
          AuthFlowStatus.COMPLETED, AuthFlowStatus.EXPIRED, AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED
        }) {
      var f = fixture(status, "csrf-safe", "otp_active");
      StepVerifier.create(cancel(f, "csrf-safe"))
          .assertNext(
              r -> {
                assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.ALREADY_TERMINAL);
                assertThat(r.successfulCleanup()).isTrue();
              })
          .verifyComplete();
      assertThat(f.invalidated).hasValue(0);
      assertThat(f.cancelled).hasValue(0);
      verifyNoInteractions(f.failureEvents);
    }
  }

  @Test
  void wrongScenarioDoesNotCancel() {
    for (AuthFlowScenario scenario :
        new AuthFlowScenario[] {AuthFlowScenario.LOGIN, AuthFlowScenario.REGISTRATION}) {
      var f = new Fixture(session(scenario, AuthFlowStatus.STARTED, "csrf-safe", "otp_active"));
      StepVerifier.create(cancel(f, "csrf-safe"))
          .assertNext(
              r -> {
                assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.INVALID_AUTH_FLOW);
                assertThat(r.successfulCleanup()).isFalse();
                assertThat(r.clearAuthFlowCookie()).isFalse();
                assertThat(r.errors())
                    .singleElement()
                    .satisfies(
                        error ->
                            assertThat(error.code())
                                .isEqualTo(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value()));
              })
          .verifyComplete();
      assertThat(f.invalidated).hasValue(0);
      assertThat(f.cancelled).hasValue(0);
      verifyNoInteractions(f.failureEvents);
    }
  }

  @Test
  void blankCsrfIsRejected() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    StepVerifier.create(cancel(f, null))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CSRF_INVALID);
              assertThat(r.errors())
                  .singleElement()
                  .satisfies(
                      error ->
                          assertThat(error.code())
                              .isEqualTo(ForgotPasswordErrorKey.CSRF_INVALID.value()));
            })
        .verifyComplete();
    assertThat(f.invalidated).hasValue(0);
    assertThat(f.cancelled).hasValue(0);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void mismatchedCsrfIsRejected() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    StepVerifier.create(cancel(f, "csrf-wrong"))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CSRF_INVALID))
        .verifyComplete();
    assertThat(f.cancelled).hasValue(0);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void disallowedOriginIsRejected() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    var service = service(f, List.of("https://other.example"));
    StepVerifier.create(
            service.cancel(
                new CancelForgotPasswordCommand(
                    SID, "csrf-safe", "https://attacker.example", null, "req-1")))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CSRF_INVALID))
        .verifyComplete();
    assertThat(f.cancelled).hasValue(0);
  }

  @Test
  void disallowedRefererIsRejected() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    StepVerifier.create(
            service(f)
                .cancel(
                    new CancelForgotPasswordCommand(
                        SID,
                        "csrf-safe",
                        null,
                        "https://attacker.example/forgot-password",
                        "req-1")))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.CSRF_INVALID))
        .verifyComplete();
    assertThat(f.cancelled).hasValue(0);
    assertThat(f.invalidated).hasValue(0);
    verifyNoInteractions(f.failureEvents);
  }

  @Test
  void persistenceErrorFailsClosedAsSystemFailure() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    when(f.cancellation.cancel(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("ambig")));
    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelForgotPasswordStatus.SYSTEM_FAILED);
              assertThat(r.successfulCleanup()).isFalse();
              assertThat(r.clearAuthFlowCookie()).isFalse();
              assertThat(r.clearXsrfCookie()).isFalse();
            })
        .verifyComplete();
    verify(f.otp, times(1)).invalidateChallenge(any());
    verify(f.cancellation, times(1)).cancel(any(), any(), any());
  }

  @Test
  void otpInvalidationFailurePreventsSessionCancellation() {
    var f = fixture(AuthFlowStatus.OTP_SENT, "csrf-safe", "otp_active");
    when(f.otp.invalidateChallenge(any()))
        .thenReturn(Mono.error(new IllegalStateException("otp invalidation failed")));

    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(CancelForgotPasswordStatus.SYSTEM_FAILED);
              assertThat(result.clearAuthFlowCookie()).isFalse();
              assertThat(result.clearXsrfCookie()).isFalse();
            })
        .verifyComplete();

    verify(f.otp, times(1)).invalidateChallenge(any());
    verify(f.cancellation, never()).cancel(any(), any(), any());
  }

  @Test
  void emptySessionLoadRemainsAnEmptyMono() {
    var f = fixture(AuthFlowStatus.STARTED, "csrf-safe", null);
    when(f.sessions.load(SID)).thenReturn(Mono.empty());

    StepVerifier.create(cancel(f, "csrf-safe")).verifyComplete();

    verify(f.otp, never()).invalidateChallenge(any());
    verify(f.cancellation, never()).cancel(any(), any(), any());
  }

  @Test
  void explicitRepeatAfterCancellationDoesNotRepeatCleanupWrites() {
    var f = fixture(AuthFlowStatus.STARTED, "csrf-safe", null);
    when(f.sessions.load(SID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    session(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.STARTED,
                        "csrf-safe",
                        null))),
            Mono.just(
                Optional.of(
                    session(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.STARTED,
                        "csrf-safe",
                        null))),
            Mono.just(
                Optional.of(
                    session(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.CANCELLED,
                        "csrf-safe",
                        null))));
    var service = service(f);
    var command =
        new CancelForgotPasswordCommand(
            SID,
            "csrf-safe",
            "https://portal.test",
            "https://portal.test/forgot-password",
            "req-1");

    StepVerifier.create(
            Mono.defer(() -> service.cancel(command))
                .concatWith(Mono.defer(() -> service.cancel(command))))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CancelForgotPasswordStatus.CANCELLED))
        .assertNext(
            result ->
                assertThat(result.status()).isEqualTo(CancelForgotPasswordStatus.ALREADY_CANCELLED))
        .verifyComplete();

    verify(f.cancellation, times(1)).cancel(any(), any(), any());
    verify(f.otp, never()).invalidateChallenge(any());
  }

  @Test
  void commandRedactsTransportSecrets() {
    var command =
        new CancelForgotPasswordCommand(
            SID, "csrf_secret", "https://portal.test", "https://portal.test/fp", "req-1");
    assertThat(command.toString()).doesNotContain("csrf_secret", "afs_fp_cancel");
  }

  @Test
  void sessionReadFailurePublishesOneSanitizedCauseFreeIncidentAndReceipt() {
    var f = new Fixture(null);
    when(f.sessions.load(SID))
        .thenReturn(Mono.error(new IllegalStateException("sessionId=secret")));

    var command =
        new CancelForgotPasswordCommand(
            SID,
            "csrf-safe",
            "https://portal.test",
            "https://portal.test/forgot-password",
            "req cancel");
    StepVerifier.create(service(f).cancel(command))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(CancelForgotPasswordStatus.SYSTEM_FAILED);
              assertThat(result.incidentPublished()).isTrue();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(f.failureEvents, times(1)).publish(event.capture());
    assertThat(event.getValue().event()).isEqualTo("forgot-password.cancel.failed");
    assertThat(event.getValue().requestId()).isEqualTo("req?cancel");
    assertThat(event.getValue().capability()).isEqualTo("FORGOT_PASSWORD");
    assertThat(event.getValue().operation()).isEqualTo("CANCEL");
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().internalCode()).isEqualTo("CANCEL_TECHNICAL_FAILURE");
    assertThat(event.getValue().publicCode()).isEqualTo("err.system.unavailable");
    assertThat(event.getValue().httpStatus()).isEqualTo(503);
    assertThat(event.getValue().retryable()).isFalse();
    assertThat(event.getValue().safeDimensions())
        .containsExactly(java.util.Map.entry("stage", "session-read"));
  }

  @Test
  void failureEventPublicationFailureCreatesNoReceiptAndIsNotRetried() {
    var f = new Fixture(null);
    when(f.sessions.load(SID)).thenReturn(Mono.error(new IllegalStateException("session read")));
    doThrow(new IllegalStateException("publisher secret"))
        .when(f.failureEvents)
        .publish(any(FailureEvent.class));

    StepVerifier.create(cancel(f, "csrf-safe"))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(CancelForgotPasswordStatus.SYSTEM_FAILED);
              assertThat(result.incidentPublished()).isFalse();
            })
        .verifyComplete();

    verify(f.failureEvents, times(1)).publish(any(FailureEvent.class));
  }

  @Test
  void synchronousTechnicalReporterUsesTheApproved403ContractAndFixedStage() {
    var f = fixture(AuthFlowStatus.STARTED, "csrf-safe", null);

    assertThat(service(f).reportSynchronousTechnicalFailure("req-cancel")).isTrue();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(f.failureEvents).publish(event.capture());
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().httpStatus()).isEqualTo(403);
    assertThat(event.getValue().publicCode())
        .isEqualTo(ForgotPasswordErrorKey.CSRF_INVALID.value());
    assertThat(event.getValue().retryable()).isFalse();
    assertThat(event.getValue().safeDimensions())
        .containsExactly(java.util.Map.entry("stage", "controller-sync"));
  }

  @Test
  void synchronousPublicationFailureReturnsNoReceiptAndDoesNotRetryPublication() {
    var f = fixture(AuthFlowStatus.STARTED, "csrf-safe", null);
    doThrow(new IllegalStateException("publisher secret"))
        .when(f.failureEvents)
        .publish(any(FailureEvent.class));

    assertThat(service(f).reportSynchronousTechnicalFailure("req-cancel")).isFalse();

    verify(f.failureEvents, times(1)).publish(any(FailureEvent.class));
  }

  private static Fixture fixture(AuthFlowStatus status, String csrfRaw, String otp) {
    return new Fixture(session(AuthFlowScenario.FORGOT_PASSWORD, status, csrfRaw, otp));
  }

  private static CancelForgotPasswordService service(Fixture f) {
    return service(f, ALLOWED);
  }

  private static CancelForgotPasswordService service(Fixture f, List<String> allowed) {
    return new CancelForgotPasswordService(
        f.sessions,
        new AuthFlowSessionCsrfService(f.sessions),
        f.otp,
        f.cancellation,
        Clock.fixed(NOW, ZoneOffset.UTC),
        f.failureEvents,
        allowed);
  }

  private static Mono<CancelForgotPasswordResult> cancel(Fixture f, String csrf) {
    return service(f)
        .cancel(
            new CancelForgotPasswordCommand(
                SID,
                csrf,
                "https://portal.test",
                "https://portal.test/forgot-password",
                "req-cancel"));
  }

  private static AuthFlowSession session(
      AuthFlowScenario scenario, AuthFlowStatus status, String csrfRaw, String otp) {
    return new AuthFlowSession(
        SID,
        scenario,
        status,
        sha256(csrfRaw),
        otp,
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(1800));
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static final class Fixture {
    final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
    final OtpChallengePort otp = mock(OtpChallengePort.class);
    final AuthFlowSessionCancellationPort cancellation =
        mock(AuthFlowSessionCancellationPort.class);
    final FailureEventPort failureEvents = mock(FailureEventPort.class);
    final AtomicInteger invalidated = new AtomicInteger();
    final AtomicInteger cancelled = new AtomicInteger();
    final AtomicReference<OtpChallengeInvalidationReason> reason = new AtomicReference<>();
    final AtomicReference<AuthFlowSessionCancellationReason> cancelReason = new AtomicReference<>();
    final AtomicReference<Instant> cancelledAt = new AtomicReference<>();

    Fixture(AuthFlowSession initial) {
      when(sessions.load(SID)).thenReturn(Mono.just(Optional.ofNullable(initial)));
      when(otp.invalidateChallenge(any(InvalidateOtpChallengeCommand.class)))
          .thenAnswer(
              invocation -> {
                invalidated.incrementAndGet();
                reason.set(invocation.getArgument(0, InvalidateOtpChallengeCommand.class).reason());
                return Mono.empty();
              });
      when(cancellation.cancel(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                cancelled.incrementAndGet();
                cancelReason.set(
                    invocation.getArgument(1, AuthFlowSessionCancellationReason.class));
                cancelledAt.set(invocation.getArgument(2, Instant.class));
                return Mono.just(
                    new SessionCancellationResult(SessionCancellationStatus.CANCELLED));
              });
    }
  }
}
