package com.bct.ngtpa.apiservice.application.dto.auth.login.otp;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;import java.util.Objects;
public record LoginProtectedAuthFlowContextCommand(String authFlowSessionId,String csrfToken,String origin,String referer,String correlationId,AuthFlowStatus expectedStatus,boolean activeOtpRequired){public LoginProtectedAuthFlowContextCommand{Objects.requireNonNull(authFlowSessionId);Objects.requireNonNull(csrfToken);Objects.requireNonNull(expectedStatus);}}
