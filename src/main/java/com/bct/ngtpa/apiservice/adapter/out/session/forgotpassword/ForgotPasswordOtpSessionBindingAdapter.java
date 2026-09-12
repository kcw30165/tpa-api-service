package com.bct.ngtpa.apiservice.adapter.out.session.forgotpassword;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.*;
import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

public final class ForgotPasswordOtpSessionBindingAdapter
    implements ForgotPasswordOtpSessionBindingPort {
  private static final String EXPIRES_AT = "forgotPasswordOtpExpiresAt",
      RESEND_AT = "forgotPasswordOtpResendAvailableAt";
  private final AuthFlowSessionPort sessions;
  private final OtpChallengeStatePort challenges;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;

  public ForgotPasswordOtpSessionBindingAdapter(
      AuthFlowSessionPort s,
      OtpChallengeStatePort c,
      ForgotPasswordAuthFlowContextProtectorPort p) {
    sessions = Objects.requireNonNull(s);
    challenges = Objects.requireNonNull(c);
    protector = Objects.requireNonNull(p);
  }

  public Mono<ForgotPasswordOtpBindingResult> apply(ForgotPasswordOtpBindingCommand c) {
    if (c == null
        || c.sessionId() == null
        || c.mutationLock() == null
        || !c.mutationLock().acquired()
        || c.deliveryStatus() == null
        || c.now() == null) return rejected();
    return sessions
        .loadSnapshot(c.sessionId())
        .flatMap(o -> o.isEmpty() ? rejected() : applyReloaded(o.get().session(), c))
        .onErrorResume(e -> rejected());
  }

  private Mono<ForgotPasswordOtpBindingResult> applyReloaded(
      AuthFlowSession s, ForgotPasswordOtpBindingCommand c) {
    if (s.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || s.status() != AuthFlowStatus.IDENTITY_VERIFIED
        || s.isExpired(c.now())
        || s.isTerminal()) return rejected();
    var context = decrypt(s);
    if (context.phase() != ForgotPasswordProtectedPhase.IDENTITY_VERIFIED) return rejected();
    return switch (c.deliveryStatus()) {
      case FAILED ->
          Mono.just(
              new ForgotPasswordOtpBindingResult(
                  ForgotPasswordOtpBindingResult.Status.CONFIRMED_FAILED));
      case UNKNOWN_OUTCOME ->
          update(
                  s,
                  context.withPhase(ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN),
                  null,
                  null,
                  null,
                  c.now())
              .thenReturn(
                  new ForgotPasswordOtpBindingResult(
                      ForgotPasswordOtpBindingResult.Status.UNKNOWN_OUTCOME));
      case ACCEPTED -> accepted(s, context, c);
    };
  }

  private Mono<ForgotPasswordOtpBindingResult> accepted(
      AuthFlowSession s, ForgotPasswordAuthFlowContext context, ForgotPasswordOtpBindingCommand c) {
    if (blank(c.otpChallengeId())
        || blank(c.contactChannel())
        || blank(c.protectedDestination())
        || blank(c.maskedDestination())
        || c.challengeExpiresAt() == null
        || c.resendAvailableAt() == null) return rejected();
    return challenges
        .findById(c.otpChallengeId())
        .flatMap(
            o -> {
              if (o.isEmpty()) return rejected();
              OtpChallenge q = o.get();
              if (q.scenario() != OtpScenario.FORGOT_PASSWORD
                  || q.status() != OtpStatus.CREATED
                  || !c.sessionId().value().equals(q.workflowRef())
                  || !q.otpChallengeId().equals(c.otpChallengeId())
                  || q.expiresAt() == null
                  || !c.now().isBefore(q.expiresAt())
                  || !q.expiresAt().equals(c.challengeExpiresAt())) return rejected();
              var bound =
                  new ForgotPasswordAuthFlowContext(
                      context.username(),
                      context.identityValue(),
                      context.identityType(),
                      true,
                      context.resetSubjectId(),
                      c.contactChannel(),
                      c.protectedDestination(),
                      c.maskedDestination(),
                      context.resetAuthorization(),
                      context.workflowVersion(),
                      ForgotPasswordProtectedPhase.OTP_SENT);
              return update(
                      s,
                      bound,
                      c.otpChallengeId(),
                      c.challengeExpiresAt(),
                      c.resendAvailableAt(),
                      c.now())
                  .thenReturn(
                      new ForgotPasswordOtpBindingResult(
                          ForgotPasswordOtpBindingResult.Status.BOUND));
            });
  }

  private ForgotPasswordAuthFlowContext decrypt(AuthFlowSession s) {
    Map<String, String> a = s.data().attributes();
    return protector.unprotect(
        new ForgotPasswordAuthFlowEncryptedContext(
            a.get(CONTEXT_CIPHERTEXT),
            a.get(CONTEXT_KEY_VERSION),
            a.getOrDefault(
                CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)));
  }

  private Mono<Void> update(
      AuthFlowSession s,
      ForgotPasswordAuthFlowContext context,
      String challenge,
      Instant expires,
      Instant resend,
      Instant now) {
    var e = protector.protect(context);
    var a = new LinkedHashMap<>(s.data().attributes());
    a.put(CONTEXT_CIPHERTEXT, e.ciphertext());
    a.put(CONTEXT_KEY_VERSION, e.keyVersion());
    a.put(CONTEXT_ALGORITHM, e.algorithm());
    if (expires == null) {
      a.remove(EXPIRES_AT);
      a.remove(RESEND_AT);
    } else {
      a.put(EXPIRES_AT, expires.toString());
      a.put(RESEND_AT, resend.toString());
    }
    var data = new AuthFlowSessionData(context.contactChannel(), context.maskedDestination(), a);
    return new com.bct.ngtpa.apiservice.application.usecase.session.authflow
            .RevisionAwareAuthFlowSessionWriter(sessions)
        .updateExpected(
            s,
            new AuthFlowSession(
                s.authFlowSessionId(),
                s.scenario(),
                AuthFlowStatus.OTP_SENT,
                s.csrfTokenHash(),
                challenge,
                data,
                s.createdAt(),
                now,
                s.expiresAt()))
        .then();
  }

  private Mono<ForgotPasswordOtpBindingResult> rejected() {
    return Mono.just(
        new ForgotPasswordOtpBindingResult(ForgotPasswordOtpBindingResult.Status.REJECTED));
  }

  private boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
