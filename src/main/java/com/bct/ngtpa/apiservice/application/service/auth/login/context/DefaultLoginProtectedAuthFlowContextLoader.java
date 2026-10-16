package com.bct.ngtpa.apiservice.application.service.auth.login.context;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class DefaultLoginProtectedAuthFlowContextLoader
    implements LoginProtectedAuthFlowContextLoader {
  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultLoginProtectedAuthFlowContextLoader.class);
  private final AuthFlowSessionLifecycleService lifecycle;
  private final AuthFlowSessionCsrfService csrf;
  private final LoginPreAuthContextProtectorPort protector;
  private final Clock clock;

  public DefaultLoginProtectedAuthFlowContextLoader(
      AuthFlowSessionLifecycleService l,
      AuthFlowSessionCsrfService c,
      LoginPreAuthContextProtectorPort p,
      Clock k) {
    lifecycle = Objects.requireNonNull(l);
    csrf = Objects.requireNonNull(c);
    protector = Objects.requireNonNull(p);
    clock = Objects.requireNonNull(k);
  }

  public Mono<LoginProtectedAuthFlowContextResult> load(LoginProtectedAuthFlowContextCommand c) {
    var id = new AuthFlowSessionId(c.authFlowSessionId());
    Instant now = clock.instant();
    return csrf.validateForSession(
            new ValidateAuthFlowSessionCsrfCommand(
                id, c.csrfToken(), now, c.origin(), c.referer(), List.of(), c.correlationId()))
        .flatMap(
            v ->
                !v.valid()
                    ? Mono.just(LoginProtectedAuthFlowContextResult.invalid(map(v.status())))
                    : lifecycle
                        .load(new LoadAuthFlowSessionCommand(id, c.correlationId()))
                        .map(o -> validate(o, c, now)))
        .onErrorReturn(
            LoginProtectedAuthFlowContextResult.invalid(
                LoginProtectedAuthFlowContextStatus.CSRF_INVALID));
  }

  private LoginProtectedAuthFlowContextResult validate(
      Optional<AuthFlowSession> o, LoginProtectedAuthFlowContextCommand c, Instant now) {
    if (o.isEmpty()) return invalid(LoginProtectedAuthFlowContextStatus.MISSING_SESSION);
    var s = o.get();
    LoginProtectedAuthFlowContextResult sessionCheck = validateSessionState(s, c, now);
    if (sessionCheck != null) {
      return sessionCheck;
    }
    Map<String, String> a = s.data() == null ? Map.of() : s.data().attributes();
    String v = a.get("loginContext.keyVersion"),
        alg = a.get("loginContext.algorithm"),
        iv = a.get("loginContext.iv"),
        ct = a.get("loginContext.ciphertext");
    if (blank(v) || blank(alg) || blank(iv) || blank(ct))
      return invalid(LoginProtectedAuthFlowContextStatus.MISSING_CONTEXT);
    return decryptAndBuildResult(s, c, v, alg, iv, ct);
  }

  private LoginProtectedAuthFlowContextResult validateSessionState(
      AuthFlowSession s, LoginProtectedAuthFlowContextCommand c, Instant now) {
    if (s.isExpired(now) || s.isTerminal())
      return invalid(LoginProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION);
    if (s.scenario() != AuthFlowScenario.LOGIN)
      return invalid(LoginProtectedAuthFlowContextStatus.WRONG_SCENARIO);
    if (s.status() != c.expectedStatus())
      return invalid(LoginProtectedAuthFlowContextStatus.INVALID_STATUS);
    if (c.activeOtpRequired() && blank(s.activeOtpChallengeId()))
      return invalid(LoginProtectedAuthFlowContextStatus.INVALID_STATUS);
    return null;
  }

  private LoginProtectedAuthFlowContextResult decryptAndBuildResult(
      AuthFlowSession s,
      LoginProtectedAuthFlowContextCommand c,
      String v,
      String alg,
      String iv,
      String ct) {
    try {
      var context = protector.unprotect(new LoginPreAuthEncryptedContext(v, alg, iv, ct));
      LoginPreAuthDestination selected = null;
      if (c.activeOtpRequired()) {
        String masked = s.data() == null ? null : s.data().maskedDestination();
        selected =
            context.destinationsById().values().stream()
                .filter(d -> Objects.equals(masked, d.maskedDestination()))
                .findFirst()
                .orElse(null);
        if (selected == null) return invalid(LoginProtectedAuthFlowContextStatus.MISSING_CONTEXT);
      }
      return LoginProtectedAuthFlowContextResult.valid(
          context, s.activeOtpChallengeId(), selected, s.csrfTokenHash());
    } catch (RuntimeException e) {
      LOG.error(
          "event=login.otp.context.failure stage=decrypt exceptionType={}",
          e.getClass().getSimpleName());
      return invalid(LoginProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED);
    }
  }

  private LoginProtectedAuthFlowContextStatus map(AuthFlowSessionCsrfValidationStatus s) {
    if (s == AuthFlowSessionCsrfValidationStatus.SESSION_MISSING)
      return LoginProtectedAuthFlowContextStatus.MISSING_SESSION;
    if (s == AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED
        || s == AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE)
      return LoginProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION;
    if (s == AuthFlowSessionCsrfValidationStatus.ORIGIN_REFERER_INVALID)
      return LoginProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID;
    return LoginProtectedAuthFlowContextStatus.CSRF_INVALID;
  }

  private LoginProtectedAuthFlowContextResult invalid(LoginProtectedAuthFlowContextStatus s) {
    LOG.warn("event=login.otp.context.rejected reason={}", s);
    return LoginProtectedAuthFlowContextResult.invalid(s);
  }

  private boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
