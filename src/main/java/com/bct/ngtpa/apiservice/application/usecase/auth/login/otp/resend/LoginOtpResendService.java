package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class LoginOtpResendService implements ResendLoginOtpUseCase {
    private final LoginProtectedAuthFlowContextLoader loader;
    private final OtpChallengePort otp;
    private final MessageDeliveryPort messages;
    private final LoginOtpSessionBindingPort binding;
    private final Clock clock;
    private final LoginOtpSendPolicy policy;

    public LoginOtpResendService(LoginProtectedAuthFlowContextLoader l, OtpChallengePort o, MessageDeliveryPort m,
            LoginOtpSessionBindingPort b, Clock c, LoginOtpSendPolicy p) {
        loader = Objects.requireNonNull(l);
        otp = Objects.requireNonNull(o);
        messages = Objects.requireNonNull(m);
        binding = Objects.requireNonNull(b);
        clock = Objects.requireNonNull(c);
        policy = Objects.requireNonNull(p);
    }

    public Mono<ResendLoginOtpResult> resend(ResendLoginOtpCommand c) {
        return loader
                .load(new LoginProtectedAuthFlowContextCommand(c.authFlowSessionId(), c.csrfToken(), c.origin(), c.referer(), c.correlationId(), com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.OTP_SENT, true))
                .flatMap(x -> x.isValid() ? replace(c, x)
                        : Mono.just(result(ResendLoginOtpStatus.AUTH_FLOW_INVALID, x, null)));
    }

    private Mono<ResendLoginOtpResult> replace(ResendLoginOtpCommand c, LoginProtectedAuthFlowContextResult x) {
        return otp.replaceChallenge(new ReplaceOtpChallengeCommand(x.activeOtpChallengeId(), policy.length(),
                policy.numericOnly(), policy.ttlSeconds(), policy.resendCooldownSeconds(), policy.resendLimitEnabled(),
                clock.instant())).flatMap(r -> switch (r.outcome()) {
                    case REPLACED -> deliver(c, x, r);
                    case COOLDOWN_ACTIVE -> Mono.just(new ResendLoginOtpResult(ResendLoginOtpStatus.COOLDOWN_ACTIVE,
                            x.selectedDestination().maskedDestination(), null, r.resendCooldownSeconds(), r.retryAfterSeconds()));
                    case EXPIRED -> Mono.just(result(ResendLoginOtpStatus.EXPIRED, x, r.retryAfterSeconds()));
                    case LOCKED, MAX_RESEND_EXCEEDED ->
                        Mono.just(result(ResendLoginOtpStatus.LOCKED, x, r.retryAfterSeconds()));
                    default -> Mono.just(result(ResendLoginOtpStatus.NOT_REPLACEABLE, x, r.retryAfterSeconds()));
                });
    }

    private Mono<ResendLoginOtpResult> deliver(ResendLoginOtpCommand c, LoginProtectedAuthFlowContextResult x,
            ReplaceOtpChallengeResult r) {
        var channel = channel(x.selectedDestination().channel()) == OtpChannel.EMAIL ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS;
        var command = new MessageDeliveryCommand(MessageDeliveryType.LOGIN_OTP, MessageDeliveryScenario.LOGIN, channel,
                x.selectedDestination().destination(), channel(x.selectedDestination().channel()) == OtpChannel.EMAIL ? policy.emailSubject() : null,
                policy.messageTemplate(), null,
                Map.of("code", r.rawCode(), "ttlValue", Integer.toString(policy.ttlSeconds()), "ttlUnit", "seconds"),
                c.acceptLanguage(), c.correlationId());
        return messages.deliver(command)
                .flatMap(d -> d.status() == MessageDeliveryStatus.ACCEPTED
                        ? binding
                                .bind(new LoginOtpSessionBindingCommand(c.authFlowSessionId(), r.previousOtpChallengeId(), r.newOtpChallengeId(), com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.OTP_SENT, com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.OTP_SENT, c.correlationId()))
                                .thenReturn(new ResendLoginOtpResult(ResendLoginOtpStatus.RESENT, x.selectedDestination().maskedDestination(),
                                        r.expiresInSeconds(), r.resendCooldownSeconds(), null))
                        : Mono.just(result(ResendLoginOtpStatus.DELIVERY_FAILED, x, null)));
    }

    private OtpChannel channel(String value) { return "email".equalsIgnoreCase(value) ? OtpChannel.EMAIL : OtpChannel.SMS; }

    private ResendLoginOtpResult result(ResendLoginOtpStatus s, LoginProtectedAuthFlowContextResult x, Integer retry) {
        return new ResendLoginOtpResult(s, x == null ? null : x.selectedDestination().maskedDestination(), null, null, retry);
    }
}
