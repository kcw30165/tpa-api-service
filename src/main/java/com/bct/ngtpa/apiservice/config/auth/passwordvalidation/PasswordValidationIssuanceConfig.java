package com.bct.ngtpa.apiservice.config.auth.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.PasswordValidationRequestParser;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.ApimPasswordValidationAdapter;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.port.in.IssuePasswordValidationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.PasswordCheckPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.*;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class PasswordValidationIssuanceConfig {
  @Bean
  ApimPasswordValidationAdapter apimPasswordValidationAdapter(ApimExchangeExecutor executor) {
    return new ApimPasswordValidationAdapter(executor);
  }

  @Bean
  PasswordValidationRequestParser passwordValidationRequestParser(
      AuthWorkflowProperties properties) {
    var credential = properties.getLogin().getCredential();
    return new PasswordValidationRequestParser(
        new JsonOperationPayloadCanonicalizer(),
        credential.getPasswordMinLength(),
        credential.getPasswordMaxLength());
  }

  @Bean
  IssuePasswordValidationUseCase issuePasswordValidationUseCase(
      PasswordValidationThrottleService throttle,
      PasswordCheckPort passwords,
      PasswordValidationGrantService grants,
      RevokeAuthenticatedSessionUseCase revocation,
      Clock clock,
      FailureEventPort failures) {
    return new PasswordValidationIssuanceService(
        throttle, passwords, grants, revocation, clock, failures, grants.ttlSeconds());
  }
}
