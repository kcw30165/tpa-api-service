package com.bct.ngtpa.apiservice.adapter.out.session.login;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpVerifiedSessionPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import reactor.core.publisher.Mono;

public final class LoginOtpVerifiedSessionAdapter implements LoginOtpVerifiedSessionPort {
  private final AuthFlowSessionLifecycleService lifecycle;

  public LoginOtpVerifiedSessionAdapter(AuthFlowSessionLifecycleService l) {
    lifecycle = l;
  }

  public Mono<Void> markVerified(String id, String correlation) {
    return Mono.defer(
            () ->
                lifecycle.updateStatus(
                    new UpdateAuthFlowStatusCommand(
                        new AuthFlowSessionId(id), AuthFlowStatus.OTP_VERIFIED, correlation)))
        .then();
  }
}
