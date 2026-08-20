package com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.in.CancelLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

@Slf4j
public final class CancelLoginService implements CancelLoginUseCase {
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionCsrfService csrf;
  private final OtpChallengePort otp;
  private final AuthFlowSessionCancellationPort cancellation;
  private final Clock clock;

  public CancelLoginService(
      AuthFlowSessionPort s,
      AuthFlowSessionCsrfService c,
      OtpChallengePort o,
      AuthFlowSessionCancellationPort x,
      Clock k) {
    sessions = Objects.requireNonNull(s);
    csrf = Objects.requireNonNull(c);
    otp = Objects.requireNonNull(o);
    cancellation = Objects.requireNonNull(x);
    clock = Objects.requireNonNull(k);
  }

  public Mono<CancelLoginResult> cancel(CancelLoginCommand c) {
    Instant now = clock.instant();
    return sessions
        .load(c.authFlowSessionId())
        .flatMap(
            v ->
                v.map(s -> cancelExisting(c, s, now))
                    .orElseGet(() -> Mono.just(clean(CancelLoginStatus.SESSION_NOT_FOUND))))
        .onErrorReturn(system());
  }

  private Mono<CancelLoginResult> cancelExisting(
      CancelLoginCommand c, AuthFlowSession s, Instant now) {
    if (s.scenario() != AuthFlowScenario.LOGIN)
      return Mono.just(
          fail(CancelLoginStatus.INVALID_AUTH_FLOW, LoginErrorKey.AUTH_FLOW_INVALID.value()));
    if (s.status() == AuthFlowStatus.CANCELLED)
      return Mono.just(clean(CancelLoginStatus.ALREADY_CANCELLED));
    if (s.status() == AuthFlowStatus.COMPLETED
        || s.status() == AuthFlowStatus.EXPIRED
        || s.isExpired(now)) return Mono.just(clean(CancelLoginStatus.ALREADY_TERMINAL));
    log.info(
        "Canceling login session {} with status {} and scenario {} and csrfToken {}",
        s.authFlowSessionId(),
        s.status(),
        s.scenario(),
        c.csrfToken());
    if (c.csrfToken() == null || c.csrfToken().isBlank())
      return Mono.just(fail(CancelLoginStatus.CSRF_INVALID, LoginErrorKey.CSRF_INVALID.value()));
    return csrf.validateForSession(
            new ValidateAuthFlowSessionCsrfCommand(
                c.authFlowSessionId(),
                c.csrfToken(),
                now,
                c.origin(),
                c.referer(),
                List.of(),
                c.correlationId()))
        .flatMap(
            v ->
                !v.valid()
                    ? Mono.just(
                        fail(CancelLoginStatus.CSRF_INVALID, LoginErrorKey.CSRF_INVALID.value()))
                    : invalidate(s, now)
                        .then(
                            cancellation.cancel(
                                s.authFlowSessionId(),
                                AuthFlowSessionCancellationReason.USER_CANCELLED,
                                now))
                        .map(r -> map(r.status())))
        .onErrorReturn(system());
  }

  private Mono<Void> invalidate(AuthFlowSession s, Instant now) {
    return s.activeOtpChallengeId() == null || s.activeOtpChallengeId().isBlank()
        ? Mono.empty()
        : otp.invalidateChallenge(
            new InvalidateOtpChallengeCommand(
                s.activeOtpChallengeId(),
                OtpChallengeInvalidationReason.USER_CANCELLED_LOGIN,
                now));
  }

  private CancelLoginResult map(SessionCancellationStatus s) {
    return switch (s) {
      case CANCELLED -> clean(CancelLoginStatus.CANCELLED);
      case ALREADY_CANCELLED -> clean(CancelLoginStatus.ALREADY_CANCELLED);
      case ALREADY_TERMINAL -> clean(CancelLoginStatus.ALREADY_TERMINAL);
      case NOT_FOUND -> clean(CancelLoginStatus.SESSION_NOT_FOUND);
    };
  }

  private CancelLoginResult clean(CancelLoginStatus s) {
    return new CancelLoginResult(s, true, true, List.of());
  }

  private CancelLoginResult fail(CancelLoginStatus s, String code) {
    return new CancelLoginResult(s, false, false, List.of(ApiErrorDescriptorSupport.error(code)));
  }

  private CancelLoginResult system() {
    return fail(CancelLoginStatus.SYSTEM_FAILED, SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
  }

  private static final class ApiErrorDescriptorSupport {
    static WorkflowErrorDescriptor error(String code) {
      return new WorkflowErrorDescriptor(
          WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "BFF");
    }
  }
}
