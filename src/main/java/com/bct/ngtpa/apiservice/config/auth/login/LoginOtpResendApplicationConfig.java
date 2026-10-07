package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpResendWebMapper;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.*;
import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class LoginOtpResendApplicationConfig {
  @Bean
  LoginOtpResendWebMapper loginOtpResendWebMapper() {
    return new LoginOtpResendWebMapper();
  }

  @Bean
  ResendLoginOtpUseCase resendLoginOtpUseCase(
      LoginProtectedAuthFlowContextLoader loader,
      OtpChallengePort otp,
      MessageDeliveryPort messages,
      LoginOtpSessionBindingPort binding,
      IdempotencyExecutor idempotencyExecutor,
      Clock clock,
      AuthWorkflowProperties properties,
      TransientJsonPort json,
      NoticeTemplateRegistryPort registryPort) {
    properties.validateLoginPolicy();
    var p = properties.getLogin().getOtp();
    var policy =
        new LoginOtpSendPolicy(
            p.getLength(),
            p.isNumericOnly(),
            p.getTtlSeconds(),
            p.getResendCooldownSeconds(),
            p.getMaxVerifyAttempts(),
            p.isResendLimitEnabled(),
            p.getMaxResendAttempts(),
            p.getSmsTemplate(),
            p.getEmailTemplate().getSubject());
    var core =
        new LoginOtpResendService(loader, otp, messages, binding, clock, policy, registryPort);
    return new SharedLoginOtpResendService(
        idempotencyExecutor,
        core,
        new LoginOtpResendExecutionBridge(
            json, new LoginOtpResendReplayCodec(new LoginResponseEnvelope(json))),
        new LoginIdempotencyIdentityFactory(),
        new LoginFingerprintFactory(),
        Duration.ofSeconds(properties.getLogin().getIdempotency().getOtpResendTtlSeconds()));
  }
}
