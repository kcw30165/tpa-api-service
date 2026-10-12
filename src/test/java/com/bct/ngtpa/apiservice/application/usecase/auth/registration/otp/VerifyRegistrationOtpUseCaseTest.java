package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class VerifyRegistrationOtpUseCaseTest {
  @Test
  void acquiredExecutesAndCapturesSafeVerifiedResult() {
    Fixture f = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    VerifyRegistrationOtpResult result = f.service.verify(command("idem", "123456")).block();
    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
    assertThat(f.otp.calls).isEqualTo(1);
    assertThat(f.session.transitions).isEqualTo(1);
    assertThat(f.lookup.calls).isEqualTo(1);
    assertThat(f.eligible.stores).isEqualTo(1);
    assertThat(f.executor.snapshot.serializedSafeBody())
        .contains("otp-verify", "accountEnv", "trustCode")
        .doesNotContain(
            "123456", "otp-old", "afs-secret", "csrf-secret", "member@example.com", "3024102");
    assertThat(f.executor.command.operationId().scope().value()).isNotEqualTo("afs-secret");
  }

  @Test
  void invalidOtpAndContextStopBeforeSharedExecutor() {
    Fixture invalidOtp = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    assertThat(invalidOtp.service.verify(command("idem", " ")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.VALIDATION_FAILED);
    assertThat(invalidOtp.executor.calls).isZero();
    Fixture invalidContext = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    invalidContext.context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
    assertThat(invalidContext.service.verify(command("idem", "123456")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(invalidContext.executor.calls).isZero();
  }

  @Test
  void nonExecutableSharedOutcomesHaveNoProtectedEffects() {
    for (IdempotencyReservationOutcome outcome :
        List.of(
            IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
            IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            IdempotencyReservationOutcome.FAILED_EXISTING,
            IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      Fixture f = new Fixture(outcome);
      f.service.verify(command("idem", "123456")).block();
      assertThat(f.otp.calls).isZero();
      assertThat(f.session.transitions).isZero();
      assertThat(f.lookup.calls).isZero();
      assertThat(f.eligible.stores).isZero();
    }
  }

  @Test
  void deterministicOtpAndTrustResultsAreCaptured() {
    Fixture invalid = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    invalid.otp.result = RegistrationOtpVerificationResult.invalid(3);
    VerifyRegistrationOtpResult invalidResult =
        invalid.service.verify(command("a", "111111")).block();
    assertThat(invalidResult.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_INVALID);
    assertThat(invalidResult.remainingVerifyAttempts()).isEqualTo(3);
    assertThat(invalid.executor.snapshot).isNotNull();

    Fixture expired = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    expired.otp.result = RegistrationOtpVerificationResult.expired();
    assertThat(expired.service.verify(command("b", "111111")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.OTP_EXPIRED);
    assertThat(expired.executor.snapshot).isNotNull();

    Fixture locked = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    locked.otp.result = RegistrationOtpVerificationResult.locked();
    VerifyRegistrationOtpResult lockedResult =
        locked.service.verify(command("c", "111111")).block();
    assertThat(lockedResult.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_LOCKED);
    assertThat(lockedResult.remainingVerifyAttempts()).isZero();
    assertThat(locked.executor.snapshot).isNotNull();

    Fixture none = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    none.lookup.status = RegistrationLookupStatus.NOT_ELIGIBLE;
    none.lookup.accounts = List.of();
    assertThat(none.service.verify(command("d", "123456")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS);
    assertThat(none.executor.snapshot).isNotNull();
  }

  private static VerifyRegistrationOtpCommand command(String key, String code) {
    return new VerifyRegistrationOtpCommand(
        new AuthFlowSessionId("afs-secret"),
        "csrf-secret",
        code,
        null,
        null,
        new RegistrationIdempotencyKey("test-key-" + key),
        "en",
        "corr",
        Duration.ofMinutes(5));
  }

  private static final class Fixture {
    final Context context = new Context();
    final Executor executor;
    final Otp otp = new Otp();
    final Session session = new Session();
    final Lookup lookup = new Lookup();
    final Eligible eligible = new Eligible();
    final SharedVerifyRegistrationOtpService service;

    Fixture(IdempotencyReservationOutcome outcome) {
      executor = new Executor(outcome);
      var bridge =
          new RegistrationOtpVerifySharedExecutionBridge(new RegistrationOtpVerifyReplayCodec());
      service =
          new SharedVerifyRegistrationOtpService(
              context,
              executor,
              new RegistrationOtpVerifyProtectedOperation(
                  otp,
                  session,
                  lookup,
                  eligible,
                  Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC)),
              bridge,
              new RegistrationSharedIdempotencyIdentityFactory(),
              new RegistrationSharedFingerprintFactory());
    }
  }

  private static final class Executor implements IdempotencyExecutor {
    final IdempotencyReservationOutcome outcome;
    int calls;
    IdempotentExecutionCommand command;
    IdempotentTerminalResponseSnapshot snapshot;

    Executor(IdempotencyReservationOutcome outcome) {
      this.outcome = outcome;
    }

    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      calls++;
      this.command = command;
      if (outcome == IdempotencyReservationOutcome.ACQUIRED) {
        return command
            .protectedOperation()
            .get()
            .flatMap(command.responseCapture()::capture)
            .map(
                c -> {
                  snapshot = c.snapshot();
                  return new IdempotentExecutionResult(outcome, c.response());
                });
      }
      return Mono.just(new IdempotentExecutionResult(outcome, null));
    }
  }

  private static final class Context implements RegistrationProtectedAuthFlowContextLoader {
    RegistrationProtectedAuthFlowContextResult result =
        RegistrationProtectedAuthFlowContextResult.valid(
            "otp-old",
            "m****r@example.com",
            new RegistrationAuthFlowContext(
                MemberIdentityType.ID,
                "3024102",
                "04/04/1982",
                RegistrationContactType.EMAIL,
                "member@example.com"));

    public Mono<RegistrationProtectedAuthFlowContextResult> load(
        RegistrationProtectedAuthFlowContextCommand command) {
      return Mono.just(result);
    }
  }

  private static final class Otp implements RegistrationOtpPort {
    int calls;
    RegistrationOtpVerificationResult result = RegistrationOtpVerificationResult.verified();

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<RegistrationOtpVerificationResult> verifyOtp(String active, String code) {
      calls++;
      return Mono.just(result);
    }
  }

  private static final class Session implements RegistrationSessionPort {
    int transitions;

    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Void> updateOtpChallenge(
        String sid, String otp, RegistrationContactType type, String masked) {
      return Mono.empty();
    }

    public Mono<Void> markOtpVerified(String sid) {
      transitions++;
      return Mono.empty();
    }
  }

  private static final class Lookup implements RegistrationLookupPort {
    int calls;
    RegistrationLookupStatus status = RegistrationLookupStatus.ELIGIBLE;
    List<RegistrationLookupAccount> accounts =
        List.of(
            new RegistrationLookupAccount(
                "OI", "JPM", null, null, null, null, null, Boolean.FALSE, null));

    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      calls++;
      return Mono.just(new RegistrationLookupResult(status, accounts, null));
    }
  }

  private static final class Eligible implements RegistrationEligibleTrustPersistencePort {
    int stores;

    public Mono<Void> store(
        AuthFlowSessionId sessionId, List<RegistrationLookupAccount> accounts, Instant now) {
      stores++;
      return Mono.empty();
    }
  }
}
