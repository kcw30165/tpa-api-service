package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

/** Request-local evidence that this COMPLETE invocation published a terminal incident. */
public record ForgotPasswordCompletePublicationReceipt(boolean terminalFailure) {}
