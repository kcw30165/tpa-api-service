package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class LoginOtpResendService implements ResendLoginOtpUseCase {
    private final LoginOtpResendContextLoader loader;
    private final OtpChallengePort otp;
    private final MessageDeliveryPort messages;
    private final LoginOtpReplacementBindingPort binding;
    private final Clock clock;
    private final LoginOtpSendPolicy policy;

    public LoginOtpResendService(LoginOtpResendContextLoader l, OtpChallengePort o, MessageDeliveryPort m,
            LoginOtpReplacementBindingPort b, Clock c, LoginOtpSendPolicy p) {
        loader = Objects.requireNonNull(l);
        otp = Objects.requireNonNull(o);
        messages = Objects.requireNonNull(m);
        binding = Objects.requireNonNull(b);
        clock = Objects.requireNonNull(c);
        policy = Objects.requireNonNull(p);
    }

    public Mono<ResendLoginOtpResult> resend(ResendLoginOtpCommand c) {
        return loader
                .load(new LoginOtpResendContextCommand(c.authFlowSessionId(), c.csrfToken(), c.origin(), c.referer(),
                        c.correlationId()))
                .flatMap(x -> x.valid() ? replace(c, x)
                        : Mono.just(result(ResendLoginOtpStatus.AUTH_FLOW_INVALID, x, null)));
    }

    private Mono<ResendLoginOtpResult> replace(ResendLoginOtpCommand c, LoginOtpResendContextResult x) {
        return otp.replaceChallenge(new ReplaceOtpChallengeCommand(x.activeOtpChallengeId(), policy.length(),
                policy.numericOnly(), policy.ttlSeconds(), policy.resendCooldownSeconds(), policy.resendLimitEnabled(),
                clock.instant())).flatMap(r -> switch (r.outcome()) {
                    case REPLACED -> deliver(c, x, r);
                    case COOLDOWN_ACTIVE -> Mono.just(new ResendLoginOtpResult(ResendLoginOtpStatus.COOLDOWN_ACTIVE,
                            x.maskedDestination(), null, r.resendCooldownSeconds(), r.retryAfterSeconds()));
                    case EXPIRED -> Mono.just(result(ResendLoginOtpStatus.EXPIRED, x, r.retryAfterSeconds()));
                    case LOCKED, MAX_RESEND_EXCEEDED ->
                        Mono.just(result(ResendLoginOtpStatus.LOCKED, x, r.retryAfterSeconds()));
                    default -> Mono.just(result(ResendLoginOtpStatus.NOT_REPLACEABLE, x, r.retryAfterSeconds()));
                });
    }

    private Mono<ResendLoginOtpResult> deliver(ResendLoginOtpCommand c, LoginOtpResendContextResult x,
            ReplaceOtpChallengeResult r) {
        var channel = x.channel() == LoginOtpChannel.EMAIL ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS;
        var command = new MessageDeliveryCommand(MessageDeliveryType.LOGIN_OTP, MessageDeliveryScenario.LOGIN, channel,
                x.destination(), x.channel() == LoginOtpChannel.EMAIL ? policy.emailSubject() : null,
                policy.messageTemplate(), null,
                Map.of("code", r.rawCode(), "ttlValue", Integer.toString(policy.ttlSeconds()), "ttlUnit", "seconds"),
                c.acceptLanguage(), c.correlationId());
        return messages.deliver(command)
                .flatMap(d -> d.status() == MessageDeliveryStatus.ACCEPTED
                        ? binding
                                .bind(new LoginOtpReplacementBindingCommand(c.authFlowSessionId(),
                                        r.previousOtpChallengeId(), r.newOtpChallengeId(), c.correlationId()))
                                .thenReturn(new ResendLoginOtpResult(ResendLoginOtpStatus.RESENT, x.maskedDestination(),
                                        r.expiresInSeconds(), r.resendCooldownSeconds(), null))
                        : Mono.just(result(ResendLoginOtpStatus.DELIVERY_FAILED, x, null)));
    }

    private ResendLoginOtpResult result(ResendLoginOtpStatus s, LoginOtpResendContextResult x, Integer retry) {
        return new ResendLoginOtpResult(s, x == null ? null : x.maskedDestination(), null, null, retry);
    }
}
