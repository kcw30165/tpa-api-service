package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.SharedStartRegistrationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StartRegistrationUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");

  @Test
  void acquiredExecutesProtectedWorkflowAndPreservesFingerprintContract() {
    var fixture = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    StepVerifier.create(fixture.useCase.start(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
              assertThat(result.csrfToken()).isEqualTo("csrf_token_safe");
              assertThat(result.authFlowSessionId()).isEqualTo("afs_session_for_cookie");
            })
        .verifyComplete();
    assertThat(fixture.executor.protectedCalls).isEqualTo(1);
    assertThat(fixture.lookup.calls).isEqualTo(1);
    assertThat(fixture.session.created).isEqualTo(1);
    assertThat(fixture.otp.calls).isEqualTo(1);
    assertThat(fixture.message.calls).isEqualTo(1);
    assertThat(fixture.executor.command.operationId().capability().value())
        .isEqualTo("registration");
    assertThat(fixture.executor.command.operationId().action().value()).isEqualTo("start");
    assertThat(fixture.executor.command.fingerprint().version()).isEqualTo(1);
  }

  @Test
  void completedReplayHydratesOriginalStartedResultWithoutProtectedOperation() {
    var fixture = new Fixture(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    StepVerifier.create(fixture.useCase.start(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED);
              assertThat(result.csrfToken()).isEqualTo("csrf_token_safe");
              assertThat(result.authFlowSessionId()).isEqualTo("afs_session_for_cookie");
            })
        .verifyComplete();
    assertThat(fixture.executor.protectedCalls).isZero();
    assertThat(fixture.lookup.calls).isZero();
    assertThat(fixture.session.created).isZero();
    assertThat(fixture.otp.calls).isZero();
    assertThat(fixture.message.calls).isZero();
  }

  @Test
  void nonExecutableLifecycleOutcomesNeverInvokeWorkflow() {
    for (IdempotencyReservationOutcome outcome :
        List.of(
            IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
            IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            IdempotencyReservationOutcome.FAILED_EXISTING,
            IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      var fixture = new Fixture(outcome);
      StartRegistrationResult result = fixture.useCase.start(command()).block();
      assertThat(fixture.executor.protectedCalls).isZero();
      assertThat(fixture.lookup.calls).isZero();
      if (outcome == IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS) {
        assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS);
        assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
      } else if (outcome == IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST) {
        assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED);
        assertThat(result.errors())
            .singleElement()
            .satisfies(
                error ->
                    assertThat(error.errorKey())
                        .isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED));
      } else {
        assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.SYSTEM_FAILED);
      }
    }
  }

  @Test
  void deterministicValidationAndLookupFailuresAreNormalTerminalResults() {
    var validation = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    StartRegistrationResult invalid = validation.useCase.start(invalidCommand()).block();
    assertThat(invalid.status()).isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED);
    assertThat(validation.lookup.calls).isZero();

    var notEligible = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    notEligible.lookup.eligible = false;
    StartRegistrationResult rejected = notEligible.useCase.start(command()).block();
    assertThat(rejected.status()).isEqualTo(StartRegistrationResultStatus.VERIFICATION_FAILED);
    assertThat(rejected.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.errorKey())
                    .isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED));
    assertThat(notEligible.session.created).isZero();
  }

  private static StartRegistrationCommand command() {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey("idem_abc123"),
        "GPFS",
        "1234567890",
        "31/01/1980",
        "member@example.com",
        null,
        "cap_secret",
        null,
        "en",
        "corr-secret",
        Duration.ofMinutes(5));
  }

  private static StartRegistrationCommand invalidCommand() {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey("idem_abc123"),
        "BAD",
        "x",
        "31/02/1980",
        null,
        null,
        null,
        null,
        "en",
        "corr",
        Duration.ofMinutes(5));
  }

  private static final class Fixture {
    final RecordingExecutor executor;
    final Lookup lookup = new Lookup();
    final Sessions session = new Sessions();
    final Otp otp = new Otp();
    final Message message = new Message();
    final SharedStartRegistrationService useCase;

    Fixture(IdempotencyReservationOutcome outcome) {
      var protectedReplay = new ProtectedReplay();
      var responses =
          new RegistrationStartSharedExecutionBridge(
              new RegistrationStartReplayCodec(), protectedReplay);
      this.executor = new RecordingExecutor(outcome, responses, protectedReplay);
      var operation =
          new RegistrationStartProtectedOperation(
              new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
              lookup,
              session,
              sid -> Mono.just("csrf_token_safe"),
              otp,
              message,
              null,
              null,
              Clock.fixed(NOW, ZoneOffset.UTC));
      this.useCase =
          new SharedStartRegistrationService(
              executor,
              operation,
              responses,
              new RegistrationSharedIdempotencyIdentityFactory(),
              new RegistrationSharedFingerprintFactory());
    }
  }

  private static final class RecordingExecutor implements IdempotencyExecutor {
    final IdempotencyReservationOutcome outcome;
    final RegistrationStartSharedExecutionBridge responses;
    final ProtectedReplay protectedReplay;
    int protectedCalls;
    IdempotentExecutionCommand command;

    RecordingExecutor(
        IdempotencyReservationOutcome outcome,
        RegistrationStartSharedExecutionBridge responses,
        ProtectedReplay protectedReplay) {
      this.outcome = outcome;
      this.responses = responses;
      this.protectedReplay = protectedReplay;
    }

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      this.command = command;
      if (outcome == IdempotencyReservationOutcome.ACQUIRED) {
        protectedCalls++;
        return command
            .protectedOperation()
            .get()
            .flatMap(
                response ->
                    command
                        .responseCapture()
                        .capture(response)
                        .map(
                            captured -> {
                              protectedReplay.snapshot = captured.snapshot();
                              return new IdempotentExecutionResult(outcome, response);
                            }));
      }
      if (outcome == IdempotencyReservationOutcome.COMPLETED_REPLAY) {
        protectedReplay.seed();
        return command
            .replayResolver()
            .hydrate(protectedReplay.snapshot)
            .map(response -> new IdempotentExecutionResult(outcome, response));
      }
      return Mono.just(new IdempotentExecutionResult(outcome, null));
    }
  }

  private static final class ProtectedReplay implements RegistrationStartProtectedReplayPort {
    RegistrationStartProtectedReplayState state;
    com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot snapshot;
    final ProtectedReplayReference reference =
        new ProtectedReplayReference("startReplayReference0001");

    void seed() {
      state =
          new RegistrationStartProtectedReplayState("csrf_token_safe", "afs_session_for_cookie");
      var codec = new RegistrationStartReplayCodec();
      snapshot = codec.capture(startedResult(), this, Duration.ofMinutes(5)).block().snapshot();
    }

    @Override
    public Mono<ProtectedReplayReference> store(
        RegistrationStartProtectedReplayState state, Duration ttl) {
      this.state = state;
      return Mono.just(reference);
    }

    @Override
    public Mono<RegistrationStartProtectedReplayState> load(ProtectedReplayReference reference) {
      return Mono.just(state);
    }
  }

  private static StartRegistrationResult startedResult() {
    return new StartRegistrationResult(
        StartRegistrationResultStatus.STARTED,
        WorkflowNextStep.VERIFY_OTP,
        "m****r@example.com",
        300,
        60,
        3,
        "csrf_token_safe",
        "afs_session_for_cookie",
        List.of(),
        List.of(),
        com.bct.ngtpa.apiservice.application.dto.auth.idempotency
            .RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private static final class Lookup implements RegistrationLookupPort {
    int calls;
    boolean eligible = true;

    @Override
    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      calls++;
      return Mono.just(
          eligible
              ? new RegistrationLookupResult(
                  RegistrationLookupStatus.ELIGIBLE,
                  List.of(
                      new RegistrationLookupAccount("member@example.com", "49998888", "OI", "JPM")),
                  null)
              : new RegistrationLookupResult(
                  RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), "not-found"));
    }
  }

  private static final class Sessions implements RegistrationSessionPort {
    int created;

    @Override
    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      created++;
      return Mono.just("afs_session_for_cookie");
    }

    @Override
    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      return Mono.empty();
    }
  }

  private static final class Otp implements RegistrationOtpPort {
    int calls;

    @Override
    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      calls++;
      return Mono.just(new RegistrationOtpCreationResult("otp_internal", "123456", 300, 60, 3));
    }
  }

  private static final class Message implements RegistrationMessagePort {
    int calls;

    @Override
    public Mono<Boolean> deliverRegistrationOtp(
        RegistrationContactType type,
        String recipient,
        String code,
        int ttl,
        String lang,
        String correlation) {
      calls++;
      return Mono.just(true);
    }
  }
}
