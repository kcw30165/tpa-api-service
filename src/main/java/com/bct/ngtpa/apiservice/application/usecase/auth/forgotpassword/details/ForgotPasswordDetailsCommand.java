package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;

public record ForgotPasswordDetailsCommand(
    AuthFlowSessionId sessionId,
    String csrfToken,
    String origin,
    String referer,
    String requestId,
    String dateOfBirth,
    String email,
    String mobile,
    Duration lockLease) {}
