package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;
public record ForgotPasswordOtpBindingResult(Status status){public enum Status{BOUND,CONFIRMED_FAILED,UNKNOWN_OUTCOME,REJECTED}public boolean bound(){return status==Status.BOUND;}}
