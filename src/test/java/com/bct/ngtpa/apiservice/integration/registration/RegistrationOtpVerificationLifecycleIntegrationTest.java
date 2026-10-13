package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationOtpVerificationLifecycleIntegrationTest {
  private final Context context = new Context();
  private final StatefulExecutor idempotency = new StatefulExecutor();
  private final Otp otp = new Otp();
  private final Session session = new Session();
  private final Lookup lookup = new Lookup();
  private final Eligible eligible = new Eligible();
  private final SharedVerifyRegistrationOtpService service =
      new SharedVerifyRegistrationOtpService(
          context,
          idempotency,
          new RegistrationOtpVerifyProtectedOperation(
              otp,
              session,
              lookup,
              eligible,
              Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)),
          new RegistrationOtpVerifySharedExecutionBridge(new RegistrationOtpVerifyReplayCodec()),
          new RegistrationSharedIdempotencyIdentityFactory(),
          new RegistrationSharedFingerprintFactory());

  @BeforeEach
  void reset() {
    context.reset();
    idempotency.clear();
    otp.reset();
    session.transitions = 0;
    lookup.reset();
    eligible.stores = 0;
  }

  @Test
  @RegistrationTestCase("TC-044")
  void correctOtpWithOneTrustTransitionsToUsernameCheck() {
    VerifyRegistrationOtpResult result = service.verify(command("one", "123456")).block();
    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(result.nextStep().name()).isEqualTo("USERNAME_CHECK");
    assertThat(result.unregisteredTrusts())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.accountEnv()).isEqualTo("OI");
              assertThat(t.trustCode()).isEqualTo("JPM");
            });
    assertThat(session.transitions).isEqualTo(1);
    assertThat(eligible.stores).isEqualTo(1);
  }

  @Test
  @RegistrationTestCase("TC-045")
  void correctOtpWithMultipleTrustsTransitionsToSelection() {
    lookup.accounts =
        List.of(
            new RegistrationLookupAccount(
                "OI", "JPM", null, null, null, null, null, Boolean.FALSE, null),
            new RegistrationLookupAccount(
                "DB", "DBA", null, null, null, null, null, Boolean.FALSE, null));
    VerifyRegistrationOtpResult result = service.verify(command("many", "123456")).block();
    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(result.nextStep().name()).isEqualTo("SELECT_TRUST");
    assertThat(result.unregisteredTrusts())
        .extracting(RegistrationUnregisteredTrust::trustCode)
        .containsExactly("JPM", "DBA");
  }

  @Test
  @RegistrationTestCase("TC-046")
  void noEligibleTrustsIsCompletedAndReplayable() {
    lookup.status = RegistrationLookupStatus.NOT_ELIGIBLE;
    lookup.accounts = List.of();
    VerifyRegistrationOtpResult first = service.verify(command("none", "123456")).block();
    int otpCalls = otp.calls;
    int lookupCalls = lookup.calls;
    VerifyRegistrationOtpResult replay = service.verify(command("none", "123456")).block();
    assertThat(first.status()).isEqualTo(VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.idempotencyOutcome())
        .isEqualTo(RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY);
    assertThat(otp.calls).isEqualTo(otpCalls);
    assertThat(lookup.calls).isEqualTo(lookupCalls);
  }

  @Test
  @RegistrationTestCase("TC-047")
  void wrongOtpPreservesAttemptsAndReplaysWithoutEffects() {
    otp.result = RegistrationOtpVerificationResult.invalid(4);
    VerifyRegistrationOtpResult first = service.verify(command("wrong", "111111")).block();
    int calls = otp.calls;
    VerifyRegistrationOtpResult replay = service.verify(command("wrong", "111111")).block();
    assertThat(first.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_INVALID);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.remainingVerifyAttempts()).isEqualTo(4);
    assertThat(otp.calls).isEqualTo(calls);
    assertThat(session.transitions).isZero();
    assertThat(lookup.calls).isZero();
  }

  @Test
  @RegistrationTestCase("TC-048")
  void lockedOtpIsCompletedAndReplayable() {
    otp.result = RegistrationOtpVerificationResult.locked();
    VerifyRegistrationOtpResult first = service.verify(command("locked", "111111")).block();
    int calls = otp.calls;
    VerifyRegistrationOtpResult replay = service.verify(command("locked", "111111")).block();
    assertThat(first.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_LOCKED);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.remainingVerifyAttempts()).isZero();
    assertThat(otp.calls).isEqualTo(calls);
    assertThat(lookup.calls).isZero();
  }

  @Test
  @RegistrationTestCase("TC-049")
  void sameKeyDifferentOtpIsRejectedWithoutSecondProtectedExecution() {
    service.verify(command("reuse", "123456")).block();
    int calls = otp.calls;
    VerifyRegistrationOtpResult reused = service.verify(command("reuse", "654321")).block();
    assertThat(reused.status()).isEqualTo(VerifyRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED);
    assertThat(otp.calls).isEqualTo(calls);
  }

  @Test
  @RegistrationTestCase("TC-050")
  void expiredOtpReplaysWithoutSessionOrLookupEffects() {
    otp.result = RegistrationOtpVerificationResult.expired();
    VerifyRegistrationOtpResult first = service.verify(command("expired", "123456")).block();
    int calls = otp.calls;
    VerifyRegistrationOtpResult replay = service.verify(command("expired", "123456")).block();
    assertThat(first.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_EXPIRED);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(otp.calls).isEqualTo(calls);
    assertThat(session.transitions).isZero();
    assertThat(lookup.calls).isZero();
  }

  @Test
  @RegistrationTestCase("TC-051")
  void completedVerifyReplayReturnsOriginalTrustsWithZeroAdditionalEffects() {
    lookup.accounts =
        List.of(
            new RegistrationLookupAccount(
                "OI", "JPM", null, null, null, null, null, Boolean.FALSE, null),
            new RegistrationLookupAccount(
                "DB", "DBA", null, null, null, null, null, Boolean.FALSE, null));
    VerifyRegistrationOtpResult first = service.verify(command("replay", "123456")).block();
    int otpCalls = otp.calls,
        transitions = session.transitions,
        lookupCalls = lookup.calls,
        stores = eligible.stores;
    VerifyRegistrationOtpResult replay = service.verify(command("replay", "123456")).block();
    assertThat(replay.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(replay.nextStep()).isEqualTo(first.nextStep());
    assertThat(replay.unregisteredTrusts()).containsExactlyElementsOf(first.unregisteredTrusts());
    assertThat(replay.idempotencyOutcome())
        .isEqualTo(RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY);
    assertThat(replay.replaySnapshot()).isNull();
    assertThat(otp.calls).isEqualTo(otpCalls);
    assertThat(session.transitions).isEqualTo(transitions);
    assertThat(lookup.calls).isEqualTo(lookupCalls);
    assertThat(eligible.stores).isEqualTo(stores);
  }

  @Test
  @RegistrationTestCase("TC-052")
  void sessionCsrfAndOriginGuardsStopBeforeSharedExecutor() {
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION);
    assertThat(service.verify(command("session", "123456")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
    reset();
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
    assertThat(service.verify(command("csrf", "123456")).block().status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
    reset();
    context.rejectOrigin = true;
    assertThat(
            service
                .verify(command("origin", "123456", "https://attacker.invalid"))
                .block()
                .status())
        .isEqualTo(VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
  }

  private VerifyRegistrationOtpCommand command(String key, String code) {
    return command(key, code, "https://portal.test");
  }

  private VerifyRegistrationOtpCommand command(String key, String code, String origin) {
    return new VerifyRegistrationOtpCommand(
        new AuthFlowSessionId("afs-safe-001"),
        "csrf-secret",
        code,
        origin,
        "https://portal.test/registration",
        new RegistrationIdempotencyKey("test-key-" + key),
        "en",
        "req",
        Duration.ofMinutes(5));
  }

  private void assertNoEffects() {
    assertThat(otp.calls).isZero();
    assertThat(session.transitions).isZero();
    assertThat(lookup.calls).isZero();
    assertThat(eligible.stores).isZero();
  }

  static final class StatefulExecutor implements IdempotencyExecutor {
    final Map<IdempotencyOperationId, Stored> records = new ConcurrentHashMap<>();
    int calls;
    int completed;

    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      calls++;
      Stored existing = records.get(command.operationId());
      if (existing != null) {
        if (!existing.fingerprint.equals(command.fingerprint()))
          return Mono.just(
              new IdempotentExecutionResult(
                  IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null));
        return command
            .replayResolver()
            .hydrate(existing.snapshot)
            .map(
                r ->
                    new IdempotentExecutionResult(
                        IdempotencyReservationOutcome.COMPLETED_REPLAY, r));
      }
      return command
          .protectedOperation()
          .get()
          .flatMap(command.responseCapture()::capture)
          .map(
              c -> {
                records.put(command.operationId(), new Stored(command.fingerprint(), c.snapshot()));
                completed++;
                return new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, c.response());
              });
    }

    void clear() {
      records.clear();
      calls = 0;
      completed = 0;
    }
  }

  record Stored(IdempotencyFingerprint fingerprint, IdempotentTerminalResponseSnapshot snapshot) {}

  static final class Context implements RegistrationProtectedAuthFlowContextLoader {
    RegistrationProtectedAuthFlowContextResult result;
    boolean rejectOrigin;

    void reset() {
      rejectOrigin = false;
      result = valid();
    }

    private RegistrationProtectedAuthFlowContextResult valid() {
      return RegistrationProtectedAuthFlowContextResult.valid(
          "otp-old",
          "m****r@example.com",
          new RegistrationAuthFlowContext(
              MemberIdentityType.ID,
              "3024102",
              "04/04/1982",
              RegistrationContactType.EMAIL,
              "member@example.com"));
    }

    public Mono<RegistrationProtectedAuthFlowContextResult> load(
        RegistrationProtectedAuthFlowContextCommand command) {
      return Mono.just(
          rejectOrigin
              ? RegistrationProtectedAuthFlowContextResult.invalid(
                  RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS)
              : result);
    }
  }

  static final class Otp implements RegistrationOtpPort {
    int calls;
    RegistrationOtpVerificationResult result;

    void reset() {
      calls = 0;
      result = RegistrationOtpVerificationResult.verified();
    }

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<RegistrationOtpVerificationResult> verifyOtp(String active, String code) {
      calls++;
      return Mono.just(result);
    }
  }

  static final class Session implements RegistrationSessionPort {
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

  static final class Lookup implements RegistrationLookupPort {
    RegistrationLookupStatus status;
    List<RegistrationLookupAccount> accounts;
    int calls;

    void reset() {
      status = RegistrationLookupStatus.ELIGIBLE;
      accounts =
          List.of(
              new RegistrationLookupAccount(
                  "OI", "JPM", null, null, null, null, null, Boolean.FALSE, null));
      calls = 0;
    }

    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      calls++;
      return Mono.just(new RegistrationLookupResult(status, accounts, null));
    }
  }

  static final class Eligible implements RegistrationEligibleTrustPersistencePort {
    int stores;

    public Mono<Void> store(
        AuthFlowSessionId sessionId, List<RegistrationLookupAccount> accounts, Instant now) {
      stores++;
      return Mono.empty();
    }
  }
}
