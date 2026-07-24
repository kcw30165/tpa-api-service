package com.bct.ngtpa.apiservice.adapter.out.session.login;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSessionBindingPort;import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;import java.util.Objects;import reactor.core.publisher.Mono;
public final class LoginOtpSessionBindingAdapter implements LoginOtpSessionBindingPort {private final AuthFlowSessionLifecycleService lifecycle;public LoginOtpSessionBindingAdapter(AuthFlowSessionLifecycleService lifecycle){this.lifecycle=Objects.requireNonNull(lifecycle);}public Mono<Void> bind(LoginOtpSessionBindingCommand c){var id=new AuthFlowSessionId(c.authFlowSessionId());return lifecycle.updateActiveOtpChallenge(
                new UpdateAuthFlowOtpCommand(id, c.otpChallengeId(), c.correlationId()))
        .then(Mono.defer(() -> lifecycle.updateStatus(
                new UpdateAuthFlowStatusCommand(
                        id, AuthFlowStatus.OTP_SENT, c.correlationId()))))
        .then();}}
