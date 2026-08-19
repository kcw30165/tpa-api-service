package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompleteForgotPasswordServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-02T20:00:00Z");

  @Test
  void completionIdempotencyHashExcludesPasswordsAndSessionIdentity() {
    var first = new Fixture("afs_fp_1", "Password1!");
    var second = new Fixture("afs_fp_2", "Another9!");

    complete(first, "afs_fp_1", "Password1!", "Password1!", "idem_shared");
    complete(second, "afs_fp_2", "Another9!", "Another9!", "idem_shared");

    assertThat(first.idempotency.lastStartCommand).isNotNull();
    assertThat(second.idempotency.lastStartCommand).isNotNull();
    assertThat(first.idempotency.lastStartCommand.requestHash())
        .isEqualTo(second.idempotency.lastStartCommand.requestHash());
  }

  @Test
  void successConsumesAuthorizationCompletesSessionAndReturnsToLogin() {
    var fixture = new Fixture("afs_fp", "Password1!");

    StepVerifier.create(
            fixture.service.complete(
                new CompleteForgotPasswordCommand(
                    new AuthFlowSessionId("afs_fp"),
                    "csrf_abc",
                    new ForgotPasswordIdempotencyKey("idem_abc"),
                    "Password1!",
                    "Password1!",
                    "https://portal.example",
                    "https://portal.example/page",
                    "en",
                    "req_abc",
                    Duration.ofSeconds(300))))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(CompleteForgotPasswordStatus.SUCCESS);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
              assertThat(result.clearAuthFlowCookie()).isTrue();
              assertThat(result.clearXsrfCookie()).isTrue();
            })
        .verifyComplete();

    assertThat(fixture.updatedSession).isNotNull();
    assertThat(fixture.updatedSession.status()).isEqualTo(AuthFlowStatus.COMPLETED);
    assertThat(fixture.protectedContext.resetAuthorization().consumedAt()).isEqualTo(NOW);
  }

  @Test
  void passwordInvalidReplaysWithoutDownstreamRetryOrAuthorizationConsumption() {
    var fixture = new Fixture("afs_fp", "Password1!");
    fixture.downstreamResult = ForgotPasswordCompletionResult.rejected("err.tpa.password.invalid");

    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_same");
    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_same");

    assertThat(fixture.lastResult.status())
        .isEqualTo(CompleteForgotPasswordStatus.BUSINESS_REJECTED);
    assertThat(fixture.lastResult.clearAuthFlowCookie()).isFalse();
    assertThat(fixture.downstreamCalls).isEqualTo(1);
    assertThat(fixture.protectedContext.resetAuthorization().consumedAt()).isNull();
    assertThat(fixture.sessions.session.status()).isEqualTo(AuthFlowStatus.RESET_AUTHORIZED);
  }

  @Test
  void memberNotFoundClosesFlowAndClearsCookies() {
    var fixture = new Fixture("afs_fp", "Password1!");
    fixture.downstreamResult = ForgotPasswordCompletionResult.rejected("err.tpa.member.not.found");

    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_member_missing");

    assertThat(fixture.lastResult.status())
        .isEqualTo(CompleteForgotPasswordStatus.BUSINESS_REJECTED);
    assertThat(fixture.lastResult.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(fixture.lastResult.clearAuthFlowCookie()).isTrue();
    assertThat(fixture.lastResult.clearXsrfCookie()).isTrue();
    assertThat(fixture.sessions.session.status()).isEqualTo(AuthFlowStatus.COMPLETED);
  }

  @Test
  void unknownOutcomeBlocksDifferentKeyRetryWithoutSecondDownstreamCall() {
    var fixture = new Fixture("afs_fp", "Password1!");
    fixture.downstreamFailure = new IllegalStateException("ambiguous timeout");

    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_unknown_1");
    fixture.downstreamFailure = null;
    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_unknown_2");

    assertThat(fixture.lastResult.status()).isEqualTo(CompleteForgotPasswordStatus.UNKNOWN_OUTCOME);
    assertThat(fixture.lastResult.clearAuthFlowCookie()).isFalse();
    assertThat(fixture.downstreamCalls).isEqualTo(1);
    assertThat(fixture.sessions.session.status()).isEqualTo(AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED);
  }

  @Test
  void idempotencySuppressionNeverCallsCompletionPort() {
    for (ForgotPasswordIdempotencyStartOutcome outcome :
        java.util.List.of(
            ForgotPasswordIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
            ForgotPasswordIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            ForgotPasswordIdempotencyStartOutcome.FAILED_EXISTING,
            ForgotPasswordIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      var fixture = new Fixture("afs_fp", "Password1!");
      fixture.idempotency.forcedOutcome = outcome;
      complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_suppressed");
      assertThat(fixture.downstreamCalls).as(outcome.name()).isZero();
    }
  }

  @Test
  void sameKeySamePayloadReplayDoesNotDuplicateCompletionPortCall() {
    var fixture = new Fixture("afs_fp", "Password1!");
    fixture.downstreamResult = ForgotPasswordCompletionResult.rejected("err.tpa.password.invalid");
    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_replay");
    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_replay");
    assertThat(fixture.downstreamCalls).isEqualTo(1);
  }

  @Test
  void completionServicePropagatesTrustedActorRole() {
    var fixture = new Fixture("afs_fp", "Password1!");
    complete(fixture, "afs_fp", "Password1!", "Password1!", "idem_role");
    assertThat(fixture.lastCompletionCommand.actorUserRole())
        .isEqualTo(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
  }

  private static void complete(
      Fixture fixture, String sessionId, String password, String confirmPassword, String key) {
    StepVerifier.create(
            fixture.service.complete(
                new CompleteForgotPasswordCommand(
                    new AuthFlowSessionId(sessionId),
                    "csrf_abc",
                    new ForgotPasswordIdempotencyKey(key),
                    password,
                    confirmPassword,
                    "https://portal.example",
                    "https://portal.example/page",
                    "en",
                    "req_abc",
                    Duration.ofSeconds(300))))
        .assertNext(result -> fixture.lastResult = result)
        .verifyComplete();
  }

  private static final class Fixture {
    final MutableProtector protector = new MutableProtector();
    final MutableSessions sessions = new MutableSessions();
    final MutableIdempotency idempotency = new MutableIdempotency();
    final CompleteForgotPasswordService service;
    AuthFlowSession updatedSession;
    ForgotPasswordAuthFlowContext protectedContext;
    ForgotPasswordCompletionResult downstreamResult =
        ForgotPasswordCompletionResult.reset("29/07/2026", "14:25:07");
    RuntimeException downstreamFailure;
    int downstreamCalls;
    ForgotPasswordCompletionCommand lastCompletionCommand;
    CompleteForgotPasswordResult lastResult;

    Fixture(String sessionId, String password) {
      ForgotPasswordResetAuthorization authorization =
          new ForgotPasswordResetAuthorization(
              "authz-01",
              sessionId,
              "reset-subject-01",
              NOW.minusSeconds(60),
              NOW.plusSeconds(300),
              null);
      ForgotPasswordAuthFlowContext context =
          new ForgotPasswordAuthFlowContext(
              "member01",
              "db655",
              "tas-user-01",
              "ID",
              true,
              "reset-subject-01",
              "EMAIL",
              "member@example.com",
              "m****r@example.com",
              authorization,
              "v1",
              ForgotPasswordProtectedPhase.PASSWORD_SETUP);
      var encrypted = protector.protect(context);
      protectedContext = context;
      var attributes = new LinkedHashMap<String, String>();
      attributes.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
      attributes.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
      attributes.put(CONTEXT_ALGORITHM, encrypted.algorithm());

      AuthFlowSession session =
          new AuthFlowSession(
              new AuthFlowSessionId(sessionId),
              AuthFlowScenario.FORGOT_PASSWORD,
              AuthFlowStatus.RESET_AUTHORIZED,
              new com.bct.ngtpa.apiservice.application.usecase.session.authflow
                      .Sha256CsrfTokenHasher()
                  .hash("csrf_abc"),
              null,
              new AuthFlowSessionData("EMAIL", "m****r@example.com", attributes),
              NOW.minusSeconds(120),
              NOW.minusSeconds(60),
              NOW.plusSeconds(1800));
      sessions.session = session;

      ForgotPasswordCompletionPort completion =
          command -> {
            lastCompletionCommand = command;
            downstreamCalls++;
            if (downstreamFailure != null) {
              return Mono.error(downstreamFailure);
            }
            return Mono.just(downstreamResult);
          };
      AuthFlowSessionMutationLockPort locks =
          new AuthFlowSessionMutationLockPort() {
            @Override
            public Mono<SessionLockHandle> acquire(
                AuthFlowSessionId ignoredSessionId, Duration lease) {
              return Mono.just(new SessionLockHandle(true, "lock-key", "owner"));
            }

            @Override
            public Mono<Boolean> release(SessionLockHandle handle) {
              return Mono.just(true);
            }
          };

      AuthWorkflowPolicyPort policies =
          new AuthWorkflowPolicyPort() {
            @Override
            public RegistrationAuthWorkflowPolicy registrationPolicy() {
              return new RegistrationAuthWorkflowPolicy(
                  null,
                  null,
                  null,
                  null,
                  new PasswordPolicy(8, 20, true, false, 0, "!@#$%^&*()_-"));
            }

            @Override
            public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
              return Optional.empty();
            }
          };

      service =
          new CompleteForgotPasswordService(
              sessions,
              locks,
              protector,
              completion,
              idempotency,
              new ForgotPasswordResetAuthorizationValidator(),
              new ForgotPasswordPasswordValidator(policies),
              Clock.fixed(NOW, ZoneOffset.UTC),
              Duration.ofSeconds(300),
              Duration.ofSeconds(10),
              java.util.List.of("https://portal.example"));
    }

    private final class MutableSessions implements AuthFlowSessionPort {
      private AuthFlowSession session;

      @Override
      public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
        return Mono.just(Optional.of(session));
      }

      @Override
      public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(
          AuthFlowSessionId authFlowSessionId) {
        return Mono.just(
            Optional.of(
                new AuthFlowSessionSnapshot(
                    session, new SessionRevision("11111111111111111111111111111111"))));
      }

      @Override
      public Mono<AuthFlowSessionMutationResult> updateRevisioned(
          RevisionedAuthFlowSessionMutationCommand command) {
        updatedSession = command.updated();
        session = updatedSession;
        return Mono.just(
            new AuthFlowSessionMutationResult(
                AuthFlowSessionMutationOutcome.APPLIED,
                new AuthFlowSessionSnapshot(
                    updatedSession, new SessionRevision("22222222222222222222222222222222"))));
      }

      @Override
      public Mono<AuthFlowSessionMutationResult> completeRevisioned(
          AuthFlowSessionSnapshot expected, Instant now) {
        session =
            new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.COMPLETED,
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                now,
                session.expiresAt());
        updatedSession = session;
        return Mono.just(
            new AuthFlowSessionMutationResult(
                AuthFlowSessionMutationOutcome.APPLIED,
                new AuthFlowSessionSnapshot(
                    updatedSession, new SessionRevision("33333333333333333333333333333333"))));
      }

      @Override
      public Mono<AuthFlowSession> create(
          com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand
              command) {
        return Mono.error(new UnsupportedOperationException());
      }
    }

    private final class MutableProtector implements ForgotPasswordAuthFlowContextProtectorPort {
      private ForgotPasswordAuthFlowContext context;

      @Override
      public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
        this.context = context;
        protectedContext = context;
        return new ForgotPasswordAuthFlowEncryptedContext(
            "ciphertext", "v1", ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
      }

      @Override
      public ForgotPasswordAuthFlowContext unprotect(
          ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
        return context;
      }
    }

    private final class MutableIdempotency implements ForgotPasswordIdempotencyPort {
      private ForgotPasswordIdempotencyStartCommand lastStartCommand;
      private ForgotPasswordIdempotencyStartOutcome forcedOutcome;
      private final Map<String, ForgotPasswordIdempotencyRecord> records = new LinkedHashMap<>();

      @Override
      public Mono<ForgotPasswordIdempotencyStartResult> startProcessing(
          ForgotPasswordIdempotencyStartCommand command) {
        lastStartCommand = command;
        if (forcedOutcome != null) {
          ForgotPasswordIdempotencyRecord forcedRecord =
              new ForgotPasswordIdempotencyRecord(
                  command.action(),
                  command.idempotencyKey(),
                  command.requestHash(),
                  statusFor(forcedOutcome),
                  null,
                  command.now(),
                  command.now(),
                  command.expiresAt(),
                  null);
          return Mono.just(new ForgotPasswordIdempotencyStartResult(forcedOutcome, forcedRecord));
        }
        ForgotPasswordIdempotencyRecord existing = records.get(command.idempotencyKey().value());
        if (existing != null) {
          ForgotPasswordIdempotencyStartOutcome outcome =
              existing.requestHash().equals(command.requestHash())
                  ? switch (existing.status()) {
                    case PROCESSING -> ForgotPasswordIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
                    case COMPLETED -> ForgotPasswordIdempotencyStartOutcome.COMPLETED_REPLAY;
                    case FAILED -> ForgotPasswordIdempotencyStartOutcome.FAILED_EXISTING;
                    case UNKNOWN_OUTCOME ->
                        ForgotPasswordIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING;
                  }
                  : ForgotPasswordIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
          return Mono.just(new ForgotPasswordIdempotencyStartResult(outcome, existing));
        }
        ForgotPasswordIdempotencyRecord created =
            new ForgotPasswordIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                ForgotPasswordIdempotencyStatus.PROCESSING,
                null,
                command.now(),
                command.now(),
                command.expiresAt(),
                null);
        records.put(command.idempotencyKey().value(), created);
        return Mono.just(
            new ForgotPasswordIdempotencyStartResult(
                ForgotPasswordIdempotencyStartOutcome.STARTED, created));
      }

      private ForgotPasswordIdempotencyStatus statusFor(
          ForgotPasswordIdempotencyStartOutcome outcome) {
        return switch (outcome) {
          case DUPLICATE_IN_PROGRESS -> ForgotPasswordIdempotencyStatus.PROCESSING;
          case KEY_REUSED_WITH_DIFFERENT_REQUEST, FAILED_EXISTING ->
              ForgotPasswordIdempotencyStatus.FAILED;
          case UNKNOWN_OUTCOME_EXISTING -> ForgotPasswordIdempotencyStatus.UNKNOWN_OUTCOME;
          case COMPLETED_REPLAY -> ForgotPasswordIdempotencyStatus.COMPLETED;
          case STARTED -> ForgotPasswordIdempotencyStatus.PROCESSING;
        };
      }

      @Override
      public Mono<ForgotPasswordIdempotencyRecord> markCompleted(
          ForgotPasswordIdempotencyCompleteCommand command) {
        ForgotPasswordIdempotencyRecord completed =
            new ForgotPasswordIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                ForgotPasswordIdempotencyStatus.COMPLETED,
                command.replaySnapshot(),
                command.completedAt(),
                command.completedAt(),
                command.completedAt().plus(command.ttl()),
                null);
        records.put(command.idempotencyKey().value(), completed);
        return Mono.just(completed);
      }

      @Override
      public Mono<ForgotPasswordIdempotencyRecord> markFailed(
          ForgotPasswordIdempotencyFailCommand command) {
        ForgotPasswordIdempotencyRecord failed =
            new ForgotPasswordIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                ForgotPasswordIdempotencyStatus.FAILED,
                null,
                command.failedAt(),
                command.failedAt(),
                command.failedAt().plus(command.ttl()),
                null);
        records.put(command.idempotencyKey().value(), failed);
        return Mono.just(failed);
      }

      @Override
      public Mono<ForgotPasswordIdempotencyRecord> markUnknownOutcome(
          ForgotPasswordIdempotencyUnknownOutcomeCommand command) {
        ForgotPasswordIdempotencyRecord unknown =
            new ForgotPasswordIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                ForgotPasswordIdempotencyStatus.UNKNOWN_OUTCOME,
                null,
                command.recordedAt(),
                command.recordedAt(),
                command.recordedAt().plus(command.ttl()),
                null);
        records.put(command.idempotencyKey().value(), unknown);
        return Mono.just(unknown);
      }

      @Override
      public Mono<Optional<ForgotPasswordIdempotencyRecord>> load(
          ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey idempotencyKey) {
        return Mono.just(Optional.empty());
      }

      @Override
      public Mono<Void> delete(
          ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey idempotencyKey) {
        return Mono.empty();
      }
    }
  }
}
