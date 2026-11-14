package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class VerifyForgotPasswordIdentityService
    implements VerifyForgotPasswordIdentityUseCase {
  private static final String FAILURE_EVENT = "forgot-password.identity-check.failed";
  private static final String ERROR_SYSTEM_UNAVAILABLE = "err.forgot-password.system.unavailable";

  private final ForgotPasswordProtectedAuthFlowContextLoader loader;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionMutationLockPort locks;
  private final AuthFlowSessionExpiryRefreshPort expiry;
  private final FailureEventPort failureEventPort;
  private final ForgotPasswordIdentityVerifier verifier = new ForgotPasswordIdentityVerifier();
  private final Clock clock;
  private final Duration lease;

  public VerifyForgotPasswordIdentityService(
      ForgotPasswordProtectedAuthFlowContextLoader l,
      ForgotPasswordAuthFlowContextProtectorPort p,
      AuthFlowSessionPort s,
      AuthFlowSessionMutationLockPort x,
      AuthFlowSessionExpiryRefreshPort e,
      Clock c,
      Duration lease,
      FailureEventPort failureEventPort) {
    loader = l;
    protector = p;
    sessions = s;
    locks = x;
    expiry = e;
    clock = c;
    this.lease = lease;
    this.failureEventPort = Objects.requireNonNull(failureEventPort, "failureEventPort");
    if (lease == null || lease.isZero() || lease.isNegative())
      throw new IllegalArgumentException("mutation lock lease must be positive");
  }

  public Mono<VerifyForgotPasswordIdentityResult> verify(VerifyForgotPasswordIdentityCommand c) {
    if (c == null || c.sessionId() == null)
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    if (verifier.mapPublicIdentityType(c.identityType()) == null)
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_TYPE));
    return Mono.defer(() -> locks.acquire(c.sessionId(), lease))
        .onErrorMap(error -> new IdentityTechnicalFailure("session-lock-acquire"))
        .flatMap(
            h ->
                h.acquired()
                    ? locked(c, h)
                    : Mono.just(
                        VerifyForgotPasswordIdentityResult.of(
                            VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW)))
        .onErrorResume(
            IdentityTechnicalFailure.class,
            failure -> technicalFailure(c, failure.stage()));
  }

  private Mono<VerifyForgotPasswordIdentityResult> locked(
      VerifyForgotPasswordIdentityCommand c, SessionLockHandle h) {
    Mono<VerifyForgotPasswordIdentityResult> work =
        Mono.defer(() -> sessions.loadSnapshot(c.sessionId()))
            .onErrorMap(error -> new IdentityTechnicalFailure("session-read"))
            .flatMap(o -> loadedSession(c, o));
    return Mono.usingWhen(
        Mono.just(h),
        ignored -> work,
        ignored -> locks.release(h).onErrorReturn(false).then(),
        (ignored, error) -> locks.release(h).onErrorReturn(false).then(),
        ignored -> locks.release(h).onErrorReturn(false).then());
  }

  private Mono<VerifyForgotPasswordIdentityResult> loadedSession(
      VerifyForgotPasswordIdentityCommand c, java.util.Optional<AuthFlowSessionSnapshot> o) {
    if (o.isEmpty())
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    AuthFlowSession reloaded = o.get().session();
    Instant now = Instant.now(clock);
    if (reloaded.isExpired(now)
        || reloaded.isTerminal()
        || reloaded.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || reloaded.status() != AuthFlowStatus.STARTED)
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    var q =
        new ForgotPasswordProtectedAuthFlowContextCommand(
            c.sessionId(),
            c.csrfToken(),
            c.origin(),
            c.referer(),
            c.requestId(),
            Set.of(AuthFlowStatus.STARTED),
            true,
            true);
    return Mono.defer(() -> loader.load(q))
        .onErrorMap(error -> new IdentityTechnicalFailure("protected-context-load"))
        .flatMap(r -> loadedContext(c, r, now));
  }

  private Mono<VerifyForgotPasswordIdentityResult> loadedContext(
      VerifyForgotPasswordIdentityCommand c,
      ForgotPasswordProtectedAuthFlowContextResult r,
      Instant now) {
    if (!r.isValid())
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              r.status() == ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID
                      || r.status()
                          == ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID
                  ? VerifyForgotPasswordIdentityStatus.SECURITY_REJECTED
                  : VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    var context = r.context();
    if (context.phase() != ForgotPasswordProtectedPhase.USERNAME_VERIFIED)
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    if (!verifier.matches(
        c.identityType(), c.identityValue(), context.identityType(), context.identityValue()))
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INCORRECT_ID));
    var verifiedContext = context.withPhase(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    return Mono.defer(() -> sessions.loadSnapshot(c.sessionId()))
        .onErrorMap(error -> new IdentityTechnicalFailure("session-read-before-transition"))
        .flatMap(latest -> persistVerified(c, latest, verifiedContext, now));
  }

  private Mono<VerifyForgotPasswordIdentityResult> persistVerified(
      VerifyForgotPasswordIdentityCommand command,
      java.util.Optional<AuthFlowSessionSnapshot> latest,
      ForgotPasswordAuthFlowContext verifiedContext,
      Instant now) {
    if (latest.isEmpty()) {
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    }
    AuthFlowSession current = latest.get().session();
    if (current.isExpired(now)
        || current.isTerminal()
        || current.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || current.status() != AuthFlowStatus.STARTED) {
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(
              VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
    }
    return Mono.fromCallable(() -> protector.protect(verifiedContext))
        .onErrorMap(error -> new IdentityTechnicalFailure("context-protection"))
        .flatMap(
            encrypted -> {
              return Mono.fromCallable(
                      () -> {
                        var attrs = new LinkedHashMap<>(current.data().attributes());
                        attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
                        attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
                        attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());
                        return new AuthFlowSession(
                            current.authFlowSessionId(),
                            current.scenario(),
                            AuthFlowStatus.IDENTITY_VERIFIED,
                            current.csrfTokenHash(),
                            current.activeOtpChallengeId(),
                            new AuthFlowSessionData(
                                current.data().contactType(),
                                current.data().maskedDestination(),
                                attrs),
                            current.createdAt(),
                            now,
                            current.expiresAt());
                      })
                  .onErrorMap(error -> new IdentityTechnicalFailure("session-transition"))
                  .flatMap(
                      updated ->
                          Mono.defer(
                                  () ->
                                      sessions.updateRevisioned(
                                          new RevisionedAuthFlowSessionMutationCommand(
                                              latest.get(), updated)))
                              .onErrorMap(
                                  error -> new IdentityTechnicalFailure("session-transition"))
                              .flatMap(
                                  result ->
                                      switch (result.outcome()) {
                                        case APPLIED ->
                                            Mono.just(
                                                VerifyForgotPasswordIdentityResult.of(
                                                    VerifyForgotPasswordIdentityStatus.VERIFIED));
                                        case SESSION_NOT_FOUND,
                                            SESSION_REVISION_CHANGED,
                                            EXPECTED_STATE_CHANGED,
                                            INVALID_TRANSITION ->
                                            Mono.just(
                                                VerifyForgotPasswordIdentityResult.of(
                                                    VerifyForgotPasswordIdentityStatus
                                                        .SYSTEM_FAILED));
                                        case SESSION_REVISION_MISSING,
                                            CORRUPT_STATE,
                                            SCHEMA_NOT_ESTABLISHED,
                                            SYSTEM_FAILURE ->
                                            technicalFailure(command, "session-transition");
                                      }));
            });
  }

  private Mono<VerifyForgotPasswordIdentityResult> technicalFailure(
      VerifyForgotPasswordIdentityCommand command, String stage) {
    failureEventPort.publish(
        new FailureEvent(
            FAILURE_EVENT,
            RequestIdLogSanitizer.sanitize(command.requestId()),
            "FORGOT_PASSWORD",
            "ForgotPasswordIdentityCheck",
            FailureOrigin.LOCAL_SYSTEM,
            "IDENTITY_CHECK_TECHNICAL_FAILURE",
            ERROR_SYSTEM_UNAVAILABLE,
            503,
            false,
            Map.of("stage", stage)));
    return Mono.just(
        VerifyForgotPasswordIdentityResult.incidentPublished(
            VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED));
  }

  private static final class IdentityTechnicalFailure extends RuntimeException {
    private final String stage;

    private IdentityTechnicalFailure(String stage) {
      super(null, null, false, false);
      this.stage = stage;
    }

    private String stage() {
      return stage;
    }
  }
}
