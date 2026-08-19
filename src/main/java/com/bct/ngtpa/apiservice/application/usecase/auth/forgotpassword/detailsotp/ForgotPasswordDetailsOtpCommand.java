package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

public record ForgotPasswordDetailsOtpCommand(
    AuthFlowSessionId sessionId,
    String csrfToken,
    String origin,
    String referer,
    String requestId,
    String language,
    String idempotencyKey,
    String dateOfBirth,
    String email,
    String mobile) {}
