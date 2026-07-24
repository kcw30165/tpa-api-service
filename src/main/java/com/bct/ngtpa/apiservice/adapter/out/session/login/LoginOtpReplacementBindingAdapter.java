package com.bct.ngtpa.apiservice.adapter.out.session.login;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpReplacementBindingPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class LoginOtpReplacementBindingAdapter implements LoginOtpReplacementBindingPort {
    private final AuthFlowSessionLifecycleService lifecycle;

    public LoginOtpReplacementBindingAdapter(AuthFlowSessionLifecycleService l) {
        lifecycle = Objects.requireNonNull(l);
    }

    public Mono<Void> bind(LoginOtpReplacementBindingCommand c) {
        var id = new AuthFlowSessionId(c.authFlowSessionId());
        return lifecycle.load(new LoadAuthFlowSessionCommand(id, c.correlationId())).flatMap(o -> {
            var s = o.orElseThrow(() -> new IllegalStateException("Login OTP session unavailable"));
            if (s.scenario() != AuthFlowScenario.LOGIN || s.status() != AuthFlowStatus.OTP_SENT
                    || !Objects.equals(s.activeOtpChallengeId(), c.oldOtpChallengeId()))
                return Mono.error(new IllegalStateException("Login OTP replacement state mismatch"));
            return lifecycle.updateActiveOtpChallenge(
                    new UpdateAuthFlowOtpCommand(id, c.newOtpChallengeId(), c.correlationId()));
        }).then();
    }
}
