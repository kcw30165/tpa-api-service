package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class LoginOtpResendService implements ResendLoginOtpUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(LoginOtpResendService.class);
  private final LoginProtectedAuthFlowContextLoader loader;
  private final OtpChallengePort otp;
  private final MessageDeliveryPort messages;
  private final LoginOtpSessionBindingPort binding;
  private final Clock clock;
  private final LoginOtpSendPolicy policy;
  private final NoticeTemplateRegistryPort registryPort;

  public LoginOtpResendService(
      LoginProtectedAuthFlowContextLoader l,
      OtpChallengePort o,
      MessageDeliveryPort m,
      LoginOtpSessionBindingPort b,
      Clock c,
      LoginOtpSendPolicy p,
      NoticeTemplateRegistryPort r) {
    loader = Objects.requireNonNull(l);
    otp = Objects.requireNonNull(o);
    messages = Objects.requireNonNull(m);
    binding = Objects.requireNonNull(b);
    clock = Objects.requireNonNull(c);
    policy = Objects.requireNonNull(p);
    registryPort = Objects.requireNonNull(r);
  }

  public Mono<ResendLoginOtpResult> resend(ResendLoginOtpCommand c) {
    return loader
        .load(
            new LoginProtectedAuthFlowContextCommand(
                c.authFlowSessionId(),
                c.csrfToken(),
                c.origin(),
                c.referer(),
                c.correlationId(),
                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.OTP_SENT,
                true))
        .flatMap(
            x ->
                x.isValid()
                    ? replace(c, x)
                    : Mono.just(
                        result(
                            x.status() == LoginProtectedAuthFlowContextStatus.CSRF_INVALID
                                ? ResendLoginOtpStatus.CSRF_INVALID
                                : ResendLoginOtpStatus.AUTH_FLOW_INVALID,
                            x,
                            null)))
        .doOnNext(r -> LOG.info("event=login.otp.resend.result status={}", r.status()))
        .doOnError(
            e ->
                LOG.error(
                    "event=login.otp.resend.failure exceptionType={}",
                    e.getClass().getSimpleName()));
  }

  private Mono<ResendLoginOtpResult> replace(
      ResendLoginOtpCommand c, LoginProtectedAuthFlowContextResult x) {
    MessageDeliveryChannel channel =
        channel(x.selectedDestination().channel()) == OtpChannel.EMAIL
            ? MessageDeliveryChannel.EMAIL
            : MessageDeliveryChannel.SMS;
    NoticeTemplateDefinition definition =
        registryPort.resolve(MessageDeliveryType.LOGIN_OTP, channel, c.acceptLanguage());
    return otp.replaceChallenge(
            new ReplaceOtpChallengeCommand(
                x.activeOtpChallengeId(),
                policy.length(),
                policy.numericOnly(),
                policy.ttlSeconds(),
                policy.resendCooldownSeconds(),
                policy.resendLimitEnabled(),
                clock.instant()))
        .flatMap(
            r ->
                switch (r.outcome()) {
                  case REPLACED -> deliver(c, x, r, channel, definition);
                  case COOLDOWN_ACTIVE ->
                      Mono.just(
                          new ResendLoginOtpResult(
                              ResendLoginOtpStatus.COOLDOWN_ACTIVE,
                              x.selectedDestination().maskedDestination(),
                              null,
                              r.resendCooldownSeconds(),
                              r.retryAfterSeconds()));
                  case EXPIRED ->
                      Mono.just(result(ResendLoginOtpStatus.EXPIRED, x, r.retryAfterSeconds()));
                  case LOCKED, MAX_RESEND_EXCEEDED ->
                      Mono.just(result(ResendLoginOtpStatus.LOCKED, x, r.retryAfterSeconds()));
                  default ->
                      Mono.just(
                          result(ResendLoginOtpStatus.NOT_REPLACEABLE, x, r.retryAfterSeconds()));
                });
  }

  private Mono<ResendLoginOtpResult> deliver(
      ResendLoginOtpCommand c,
      LoginProtectedAuthFlowContextResult x,
      ReplaceOtpChallengeResult r,
      MessageDeliveryChannel channel,
      NoticeTemplateDefinition definition) {
    var variables = new LinkedHashMap<String, String>();
    variables.put("code", r.rawCode());
    variables.put("ttlValue", Integer.toString(Math.max(1, policy.ttlSeconds() / 60)));
    if (definition.ttlUnit() != null) {
      variables.put("ttlUnit", definition.ttlUnit());
    }
    if (x.context().trustCode() != null) {
      variables.put("trustCode", x.context().trustCode());
    }
    if (x.context().schemeName() != null) {
      variables.put("schemeName", x.context().schemeName());
    }
    String schemeNameChi = resolveSchemeNameChi(x.context());
    if (schemeNameChi != null) {
      variables.put("schemeNameChi", schemeNameChi);
    }
    var command =
        new MessageDeliveryCommand(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryScenario.LOGIN,
            channel,
            x.selectedDestination().destination(),
            definition.subject(),
            definition.body(),
            "login-otp",
            Map.copyOf(variables),
            c.acceptLanguage(),
            c.correlationId());
    return messages
        .deliver(command)
        .flatMap(
            d ->
                d.status() == MessageDeliveryStatus.ACCEPTED
                    ? binding
                        .bind(
                            new LoginOtpSessionBindingCommand(
                                c.authFlowSessionId(),
                                r.previousOtpChallengeId(),
                                r.newOtpChallengeId(),
                                com.bct.ngtpa.apiservice.application.dto.session.authflow
                                    .AuthFlowStatus.OTP_SENT,
                                com.bct.ngtpa.apiservice.application.dto.session.authflow
                                    .AuthFlowStatus.OTP_SENT,
                                x.selectedDestination().channel(),
                                x.selectedDestination().maskedDestination(),
                                c.correlationId()))
                        .thenReturn(
                            new ResendLoginOtpResult(
                                ResendLoginOtpStatus.RESENT,
                                x.selectedDestination().maskedDestination(),
                                r.expiresInSeconds(),
                                r.resendCooldownSeconds(),
                                null))
                    : Mono.just(
                        result(
                            d.status() == MessageDeliveryStatus.UNKNOWN_OUTCOME
                                ? ResendLoginOtpStatus.UNKNOWN_OUTCOME
                                : ResendLoginOtpStatus.DELIVERY_FAILED,
                            x,
                            null)));
  }

  /**
   * Mirrors {@code UsernameRecoveryRequestProtectedOperation.resolveSchemeNameChi} (plan decision,
   * Nick, 2026-10-07): a blank {@code chin-sch-name} - confirmed to occur in real TRPMemberLogin
   * responses - falls back to {@code sch-name}, rather than rendering an empty Chinese scheme name
   * into a live member email.
   */
  private static String resolveSchemeNameChi(LoginPreAuthContext context) {
    String schemeNameChi = context.schemeNameChi();
    return (schemeNameChi == null || schemeNameChi.isBlank())
        ? context.schemeName()
        : schemeNameChi;
  }

  private OtpChannel channel(String value) {
    return "email".equalsIgnoreCase(value) ? OtpChannel.EMAIL : OtpChannel.SMS;
  }

  private ResendLoginOtpResult result(
      ResendLoginOtpStatus s, LoginProtectedAuthFlowContextResult x, Integer retry) {
    return new ResendLoginOtpResult(
        s,
        x == null || x.selectedDestination() == null
            ? null
            : x.selectedDestination().maskedDestination(),
        null,
        null,
        retry);
  }
}
