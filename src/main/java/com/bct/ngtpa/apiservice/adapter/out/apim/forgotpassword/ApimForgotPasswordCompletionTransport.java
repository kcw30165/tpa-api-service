package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ApimForgotPasswordCompletionTransport {
    Mono<TrpResetPasswordApimEnvelope> postResetPassword(String path, TrpResetPasswordApimRequest request);
}