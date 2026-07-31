package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;
import java.util.List;
public record ForgotPasswordDetailsValidation(boolean valid, List<String> targets) {}
