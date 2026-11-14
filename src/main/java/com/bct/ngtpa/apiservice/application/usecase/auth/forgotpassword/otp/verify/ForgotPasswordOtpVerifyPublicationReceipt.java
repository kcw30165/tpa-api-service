package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

/** Request-local evidence that this invocation published a terminal incident. */
public record ForgotPasswordOtpVerifyPublicationReceipt(boolean terminalFailure) {}
