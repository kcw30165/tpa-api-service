package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public final class ForgotPasswordOtpVerifyProtectedOperation {

  private final ForgotPasswordProtectedAuthFlowContextLoader loader;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionMutationLockPort locks;
  private final AuthFlowSessionExpiryRefreshPort expiry;
  private final OtpChallengePort otp;
  private final Clock clock;
  private final Duration lease;
  private final Duration resetAuthorizationTtl;

  public ForgotPasswordOtpVerifyProtectedOperation(
      ForgotPasswordProtectedAuthFlowContextLoader loader,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      AuthFlowSessionPort sessions,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionExpiryRefreshPort expiry,
      OtpChallengePort otp,
      Clock clock,
      Duration lease,
      Duration resetAuthorizationTtl) {
    this.loader = Objects.requireNonNull(loader);
    this.protector = Objects.requireNonNull(protector);
    this.sessions = Objects.requireNonNull(sessions);
    this.locks = Objects.requireNonNull(locks);
    this.expiry = Objects.requireNonNull(expiry);
    this.otp = Objects.requireNonNull(otp);
    this.clock = Objects.requireNonNull(clock);
    this.lease = Objects.requireNonNull(lease);
    this.resetAuthorizationTtl = Objects.requireNonNull(resetAuthorizationTtl);
  }

  public Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> execute(
      VerifyForgotPasswordOtpCommand command) {
    return Mono.defer(
            () -> {
              if (command == null || command.sessionId() == null) {
                return Mono.just(
                    result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
              }
              if (!validOtp(command.code())) {
                return Mono.just(
                    result(VerifyForgotPasswordOtpStatus.OTP_INVALID, WorkflowNextStep.VERIFY_OTP));
              }
              return stage(
                      () -> locks.acquire(command.sessionId(), lease),
                      ForgotPasswordOtpVerifyIncident.LOCK_ACQUISITION)
                  .flatMap(
                      handle ->
                          handle.acquired()
                              ? locked(command, handle)
                              : Mono.just(
                                  result(
                                      VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID,
                                      WorkflowNextStep.NONE)));
            })
        .onErrorResume(
            ClassifiedTechnicalFailure.class, failure -> Mono.just(failed(failure.incident())))
        .onErrorResume(
            failure -> Mono.just(failed(ForgotPasswordOtpVerifyIncident.PROTECTED_OPERATION)));
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> locked(
      VerifyForgotPasswordOtpCommand command, SessionLockHandle handle) {
    AtomicBoolean synchronousReleaseFailed = new AtomicBoolean();
    return Mono.usingWhen(
            Mono.just(handle),
            ignored ->
                stage(
                        () -> sessions.loadSnapshot(command.sessionId()),
                        ForgotPasswordOtpVerifyIncident.SESSION_READ)
                    .flatMap(initialOptional -> validateInitialSession(command, initialOptional)),
            ignored -> releaseAfterSuccess(handle, synchronousReleaseFailed),
            (ignored, error) -> releaseQuietly(handle),
            ignored -> releaseQuietly(handle))
        .map(
            result ->
                synchronousReleaseFailed.get()
                    ? failed(ForgotPasswordOtpVerifyIncident.LOCK_RELEASE)
                    : result);
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> validateInitialSession(
      VerifyForgotPasswordOtpCommand command,
      java.util.Optional<AuthFlowSessionSnapshot> initialOptional) {
    if (initialOptional.isEmpty()) {
      return Mono.just(
          result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
    }
    AuthFlowSession initial = initialOptional.get().session();
    Instant initialNow = Instant.now(clock);
    if (initial.isExpired(initialNow)
        || initial.isTerminal()
        || initial.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || initial.status() != AuthFlowStatus.OTP_SENT) {
      return Mono.just(
          result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
    }
    return stage(
            () ->
                loader.load(
                    new ForgotPasswordProtectedAuthFlowContextCommand(
                        command.sessionId(),
                        command.csrfToken(),
                        command.origin(),
                        command.referer(),
                        command.requestId(),
                        Set.of(AuthFlowStatus.OTP_SENT),
                        true,
                        false)),
            ForgotPasswordOtpVerifyIncident.CONTEXT_LOAD)
        .flatMap(loaded -> validateLoadedContext(command, loaded));
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> validateLoadedContext(
      VerifyForgotPasswordOtpCommand command, ForgotPasswordProtectedAuthFlowContextResult loaded) {
    if (!loaded.isValid()) {
      return Mono.just(mapLoaderFailure(loaded.status()));
    }
    ForgotPasswordAuthFlowContext context = loaded.context();
    String resetSubjectId = effectiveResetSubjectId(context);
    if (context.phase() != ForgotPasswordProtectedPhase.OTP_SENT
        || blank(loaded.activeOtpChallengeId())
        || blank(resetSubjectId)) {
      return Mono.just(
          result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
    }
    Instant now = Instant.now(clock);
    return stage(
            () -> sessions.loadSnapshot(command.sessionId()),
            ForgotPasswordOtpVerifyIncident.SESSION_REREAD)
        .flatMap(
            currentOptional ->
                validateCurrentSession(command, currentOptional, context, resetSubjectId, now));
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> validateCurrentSession(
      VerifyForgotPasswordOtpCommand command,
      java.util.Optional<AuthFlowSessionSnapshot> currentOptional,
      ForgotPasswordAuthFlowContext context,
      String resetSubjectId,
      Instant now) {
    if (currentOptional.isEmpty()) {
      return Mono.just(
          result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
    }
    AuthFlowSession current = currentOptional.get().session();
    if (current.isExpired(now)
        || current.isTerminal()
        || current.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || current.status() != AuthFlowStatus.OTP_SENT
        || blank(current.activeOtpChallengeId())) {
      return Mono.just(
          result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
    }
    return verifyActiveChallenge(
        command,
        current,
        current.activeOtpChallengeId(),
        normalizeResetSubjectId(context, resetSubjectId),
        now);
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> verifyActiveChallenge(
      VerifyForgotPasswordOtpCommand command,
      AuthFlowSession session,
      String activeOtpChallengeId,
      ForgotPasswordAuthFlowContext context,
      Instant now) {
    return stage(
            () ->
                otp.verifyChallenge(
                    new VerifyOtpChallengeCommand(activeOtpChallengeId, command.code(), now)),
            ForgotPasswordOtpVerifyIncident.CHALLENGE_VERIFICATION)
        .flatMap(
            result ->
                switch (result.outcome()) {
                  case VALID -> authorize(command, session, context, now);
                  case EXPIRED ->
                      Mono.just(
                          result(
                              VerifyForgotPasswordOtpStatus.OTP_EXPIRED,
                              WorkflowNextStep.RESEND_OTP,
                              result.remainingAttempts()));
                  case LOCKED ->
                      Mono.just(
                          result(
                              VerifyForgotPasswordOtpStatus.OTP_LOCKED,
                              WorkflowNextStep.RESTART_FLOW,
                              0));
                  case INVALID, INVALIDATED, ALREADY_VERIFIED, NOT_FOUND ->
                      Mono.just(
                          result(
                              VerifyForgotPasswordOtpStatus.OTP_INVALID,
                              WorkflowNextStep.VERIFY_OTP,
                              result.remainingAttempts()));
                });
  }

  private Mono<ForgotPasswordOtpVerifyProtectedExecutionResult> authorize(
      VerifyForgotPasswordOtpCommand command,
      AuthFlowSession reloaded,
      ForgotPasswordAuthFlowContext context,
      Instant now) {
    String resetSubjectId = effectiveResetSubjectId(context);
    ForgotPasswordResetAuthorization authorization =
        new ForgotPasswordResetAuthorization(
            opaqueRef("fpra"),
            reloaded.authFlowSessionId().value(),
            resetSubjectId,
            now,
            now.plus(resetAuthorizationTtl),
            null);
    ForgotPasswordAuthFlowContext updatedContext =
        normalizeResetSubjectId(context, resetSubjectId)
            .withResetAuthorization(authorization)
            .withPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP);
    return stage(
            () -> Mono.just(protector.protect(updatedContext)),
            ForgotPasswordOtpVerifyIncident.AUTHORIZATION_PROTECTION)
        .flatMap(
            encrypted -> {
              Map<String, String> attrs = new LinkedHashMap<>(reloaded.data().attributes());
              attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
              attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
              attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());

              AuthFlowSession updated =
                  new AuthFlowSession(
                      reloaded.authFlowSessionId(),
                      reloaded.scenario(),
                      AuthFlowStatus.RESET_AUTHORIZED,
                      reloaded.csrfTokenHash(),
                      null,
                      new AuthFlowSessionData(
                          updatedContext.contactChannel(),
                          updatedContext.maskedDestination(),
                          attrs),
                      reloaded.createdAt(),
                      now,
                      reloaded.expiresAt());

              return stage(
                      () ->
                          new RevisionAwareAuthFlowSessionWriter(sessions)
                              .updateExpected(reloaded, updated),
                      ForgotPasswordOtpVerifyIncident.SESSION_UPDATE)
                  .then(
                      stage(
                          () -> expiry.refresh(command.sessionId(), now),
                          ForgotPasswordOtpVerifyIncident.EXPIRY_REFRESH))
                  .thenReturn(
                      result(
                          VerifyForgotPasswordOtpStatus.VERIFIED, WorkflowNextStep.PASSWORD_SETUP));
            });
  }

  private <T> Mono<T> stage(Supplier<Mono<T>> operation, ForgotPasswordOtpVerifyIncident incident) {
    return Mono.defer(operation)
        .onErrorMap(
            failure ->
                failure instanceof ClassifiedTechnicalFailure
                    ? failure
                    : new ClassifiedTechnicalFailure(incident));
  }

  private Mono<Void> releaseQuietly(SessionLockHandle handle) {
    try {
      return locks.release(handle).onErrorReturn(false).then();
    } catch (RuntimeException ignored) {
      return Mono.empty();
    }
  }

  private Mono<Void> releaseAfterSuccess(
      SessionLockHandle handle, AtomicBoolean synchronousReleaseFailed) {
    try {
      return locks.release(handle).onErrorReturn(false).then();
    } catch (RuntimeException ignored) {
      synchronousReleaseFailed.set(true);
      return Mono.empty();
    }
  }

  private ForgotPasswordOtpVerifyProtectedExecutionResult mapLoaderFailure(
      ForgotPasswordProtectedAuthFlowContextStatus status) {
    if (status == ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID
        || status == ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID) {
      return result(VerifyForgotPasswordOtpStatus.CSRF_INVALID, WorkflowNextStep.NONE);
    }
    return result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE);
  }

  private ForgotPasswordOtpVerifyProtectedExecutionResult result(
      VerifyForgotPasswordOtpStatus status, WorkflowNextStep nextStep) {
    return result(status, nextStep, null);
  }

  private ForgotPasswordOtpVerifyProtectedExecutionResult result(
      VerifyForgotPasswordOtpStatus status,
      WorkflowNextStep nextStep,
      Integer remainingVerifyAttempts) {
    return new ForgotPasswordOtpVerifyProtectedExecutionResult(
        new VerifyForgotPasswordOtpResult(status, nextStep, remainingVerifyAttempts, null), null);
  }

  private ForgotPasswordOtpVerifyProtectedExecutionResult failed(
      ForgotPasswordOtpVerifyIncident incident) {
    return new ForgotPasswordOtpVerifyProtectedExecutionResult(
        new VerifyForgotPasswordOtpResult(
            VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE, null, null),
        incident);
  }

  private boolean validOtp(String code) {
    return code != null && code.matches("\\d{6}");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String opaqueRef(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String effectiveResetSubjectId(ForgotPasswordAuthFlowContext context) {
    if (context == null) {
      return null;
    }
    if (!blank(context.resetSubjectId())) {
      return context.resetSubjectId().trim();
    }
    return blank(context.identityValue()) ? null : context.identityValue().trim();
  }

  private ForgotPasswordAuthFlowContext normalizeResetSubjectId(
      ForgotPasswordAuthFlowContext context, String resetSubjectId) {
    if (context == null
        || blank(resetSubjectId)
        || resetSubjectId.equals(context.resetSubjectId())) {
      return context;
    }
    return context.withResetSubjectId(resetSubjectId);
  }

  private static final class ClassifiedTechnicalFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ForgotPasswordOtpVerifyIncident incident;

    private ClassifiedTechnicalFailure(ForgotPasswordOtpVerifyIncident incident) {
      super(null, null, false, false);
      this.incident = incident;
    }

    private ForgotPasswordOtpVerifyIncident incident() {
      return incident;
    }
  }
}
