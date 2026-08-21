package com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CancelLoginServiceTest {
  @Test
  void existingLoginInvalidatesOtpThenCancels() {
    var f = new Fixture();
    f.session = session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp_active");
    StepVerifier.create(f.service.cancel(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelLoginStatus.CANCELLED);
              assertThat(r.successfulCleanup()).isTrue();
            })
        .verifyComplete();
    assertThat(f.invalidated.get()).isOne();
    assertThat(f.cancelled.get()).isOne();
  }

  @Test
  void cancelLoadsSnapshotValidatesCentrallyAndUsesSnapshotForRevisionAwareMutation() {
    var f = new Fixture();
    f.session = session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp_active");
    StepVerifier.create(f.service.cancel(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelLoginStatus.CANCELLED))
        .verifyComplete();
    assertThat(f.snapshotLoads.get()).isOne();
    assertThat(f.plainLoads.get()).isOne();
    assertThat(f.cancelledSnapshot).isNotNull();
    assertThat(f.cancelledSnapshot.session()).isSameAs(f.session);
  }

  @Test
  void revisionConflictDoesNotInvalidateOtpOrReportSuccess() {
    var f = new Fixture();
    f.session = session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp_active");
    f.cancelOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
    StepVerifier.create(f.service.cancel(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelLoginStatus.SYSTEM_FAILED);
              assertThat(r.successfulCleanup()).isFalse();
            })
        .verifyComplete();
    assertThat(f.invalidated.get()).isZero();
    assertThat(f.cancelled.get()).isZero();
  }

  @Test
  void invalidCsrfDoesNotInvalidateOtpOrCancelSession() {
    var f = new Fixture();
    f.session = session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp_active");
    CancelLoginCommand wrong =
        new CancelLoginCommand(
            new AuthFlowSessionId("afs_opaque"), "wrong", "https://portal", null, "req");
    StepVerifier.create(f.service.cancel(wrong))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelLoginStatus.CSRF_INVALID))
        .verifyComplete();
    assertThat(f.invalidated.get()).isZero();
    assertThat(f.cancelled.get()).isZero();
  }

  @Test
  void missingSessionIsSafeCleanup() {
    var f = new Fixture();
    StepVerifier.create(f.service.cancel(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CancelLoginStatus.SESSION_NOT_FOUND);
              assertThat(r.clearAuthFlowCookie()).isTrue();
              assertThat(r.clearXsrfCookie()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void wrongScenarioDoesNotCancel() {
    var f = new Fixture();
    f.session = session(AuthFlowScenario.REGISTRATION, AuthFlowStatus.OTP_SENT, "otp_active");
    StepVerifier.create(f.service.cancel(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(CancelLoginStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(f.invalidated.get()).isZero();
    assertThat(f.cancelled.get()).isZero();
  }

  static CancelLoginCommand command() {
    return new CancelLoginCommand(
        new AuthFlowSessionId("afs_opaque"), "csrf", "https://portal", null, "req");
  }

  static AuthFlowSession session(AuthFlowScenario scenario, AuthFlowStatus status, String otp) {
    Instant now = Instant.parse("2026-07-20T00:00:00Z");
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_opaque"),
        scenario,
        status,
        "hash",
        otp,
        AuthFlowSessionData.empty(),
        now,
        now,
        now.plusSeconds(300));
  }

  static final class Fixture {
    AuthFlowSession session;
    AtomicInteger invalidated = new AtomicInteger(), cancelled = new AtomicInteger();
    AtomicInteger snapshotLoads = new AtomicInteger(), plainLoads = new AtomicInteger();
    AuthFlowSessionMutationOutcome cancelOutcome = AuthFlowSessionMutationOutcome.APPLIED;
    AuthFlowSessionSnapshot cancelledSnapshot;
    CancelLoginService service;

    Fixture() {
      AuthFlowSessionPort sessions =
          new AuthFlowSessionPort() {
            public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
              plainLoads.incrementAndGet();
              return Mono.just(Optional.ofNullable(session));
            }

            public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
              snapshotLoads.incrementAndGet();
              return Mono.just(
                  Optional.ofNullable(session)
                      .map(
                          value ->
                              new AuthFlowSessionSnapshot(
                                  value, new SessionRevision("00000000000000000000000000000001"))));
            }

            public Mono<AuthFlowSessionMutationResult> cancelRevisioned(
                AuthFlowSessionSnapshot expected, Instant now) {
              cancelledSnapshot = expected;
              if (cancelOutcome == AuthFlowSessionMutationOutcome.APPLIED) {
                cancelled.incrementAndGet();
                AuthFlowSession cancelledSession =
                    new AuthFlowSession(
                        expected.session().authFlowSessionId(),
                        expected.session().scenario(),
                        AuthFlowStatus.CANCELLED,
                        expected.session().csrfTokenHash(),
                        expected.session().activeOtpChallengeId(),
                        expected.session().data(),
                        expected.session().createdAt(),
                        now,
                        expected.session().expiresAt());
                return Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED,
                        new AuthFlowSessionSnapshot(
                            cancelledSession,
                            new SessionRevision("00000000000000000000000000000002"))));
              }
              return Mono.just(AuthFlowSessionMutationResult.outcome(cancelOutcome));
            }

            public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<AuthFlowSession> update(AuthFlowSession s) {
              return Mono.just(s);
            }

            public Mono<AuthFlowSession> touch(AuthFlowSessionId i, Instant n) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<AuthFlowSession> complete(AuthFlowSessionId i, Instant n) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<AuthFlowSession> cancel(AuthFlowSessionId i, Instant n) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<Void> delete(AuthFlowSessionId i) {
              return Mono.empty();
            }
          };
      AuthFlowSessionCsrfService csrf =
          new AuthFlowSessionCsrfService(
              sessions,
              () -> "unused",
              new CsrfTokenHasher() {
                public String hash(String raw) {
                  return "hash";
                }

                public boolean matches(String raw, String hash) {
                  return "csrf".equals(raw) && "hash".equals(hash);
                }
              });
      OtpChallengePort otp =
          new OtpChallengePort() {
            public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
              invalidated.incrementAndGet();
              assertThat(c.reason()).isEqualTo(OtpChallengeInvalidationReason.USER_CANCELLED_LOGIN);
              return Mono.empty();
            }

            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }
          };
      AuthFlowSessionCancellationPort cancel =
          (id, reason, now) ->
              Mono.error(
                  new AssertionError("Login Cancel must use AuthFlowSessionPort.cancelRevisioned"));
      service =
          new CancelLoginService(
              sessions,
              csrf,
              otp,
              cancel,
              Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
    }
  }
}
