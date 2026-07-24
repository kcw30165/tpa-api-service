package com.bct.ngtpa.apiservice.application.dto.auth.login.otp;
import java.util.Objects;
public record LoginProtectedAuthFlowContextCommand(String authFlowSessionId,String csrfToken,String origin,String referer,String correlationId){public LoginProtectedAuthFlowContextCommand{Objects.requireNonNull(authFlowSessionId);Objects.requireNonNull(csrfToken);}}
