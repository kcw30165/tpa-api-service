package com.bct.ngtpa.apiservice.config.auth.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.CancelInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.PasswordValidationPurposePayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.PasswordValidationPurposePayloadDecoderRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.PasswordValidationRequestParser;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.PersonalInformationPasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.ApimPasswordValidationAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.PasswordValidationApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.PasswordValidationApimValidationConfig;
import com.bct.ngtpa.apiservice.application.port.in.IssuePasswordValidationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.PasswordCheckPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.*;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.CancelInvestmentChoicePasswordValidationPrecheckPolicy;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.ChangeInvestmentChoicePasswordValidationPrecheckPolicy;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationPurposePrecheckPolicy;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationPurposePrecheckPolicyRegistry;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PersonalInformationPasswordValidationPrecheckPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.*;

@Configuration
@Import(PasswordValidationApimValidationConfig.class)
public class PasswordValidationIssuanceConfig {
  @Bean
  ApimPasswordValidationAdapter apimPasswordValidationAdapter(
      ApimExchangeExecutor executor, PasswordValidationApimProperties properties) {
    return new ApimPasswordValidationAdapter(executor, properties);
  }

  @Bean
  PersonalInformationPasswordValidationPayloadDecoder
      personalInformationPasswordValidationPayloadDecoder() {
    return new PersonalInformationPasswordValidationPayloadDecoder();
  }

  @Bean
  ChangeInvestmentChoicePasswordValidationPayloadDecoder
      changeInvestmentChoicePasswordValidationPayloadDecoder() {
    return new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  }

  @Bean
  CancelInvestmentChoicePasswordValidationPayloadDecoder
      cancelInvestmentChoicePasswordValidationPayloadDecoder() {
    return new CancelInvestmentChoicePasswordValidationPayloadDecoder();
  }

  @Bean
  PersonalInformationPasswordValidationPrecheckPolicy
      personalInformationPasswordValidationPrecheckPolicy() {
    return new PersonalInformationPasswordValidationPrecheckPolicy();
  }

  @Bean
  ChangeInvestmentChoicePasswordValidationPrecheckPolicy
      changeInvestmentChoicePasswordValidationPrecheckPolicy(
          InvestmentChoiceSnapshotCacheService snapshots, FailureEventPort failures) {
    return new ChangeInvestmentChoicePasswordValidationPrecheckPolicy(snapshots, failures);
  }

  @Bean
  CancelInvestmentChoicePasswordValidationPrecheckPolicy
      cancelInvestmentChoicePasswordValidationPrecheckPolicy(
          InvestmentChoiceSnapshotCacheService snapshots, FailureEventPort failures) {
    return new CancelInvestmentChoicePasswordValidationPrecheckPolicy(snapshots, failures);
  }

  @Bean
  PasswordValidationPurposePrecheckPolicyRegistry passwordValidationPurposePrecheckPolicyRegistry(
      List<PasswordValidationPurposePrecheckPolicy<?>> policies) {
    return new PasswordValidationPurposePrecheckPolicyRegistry(
        policies,
        Set.of(
            com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose
                .PERSONAL_INFORMATION_UPDATE,
            com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose
                .CHANGE_INVESTMENT_CHOICE,
            com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose
                .CANCEL_INVESTMENT_CHOICE));
  }

  @Bean
  PasswordValidationPurposePayloadDecoderRegistry passwordValidationPurposePayloadDecoderRegistry(
      List<PasswordValidationPurposePayloadDecoder<?>> decoders,
      PasswordValidationPurposePrecheckPolicyRegistry policies) {
    return new PasswordValidationPurposePayloadDecoderRegistry(
        decoders, policies.supportedPurposes(), policies.payloadTypes());
  }

  @Bean
  PasswordValidationRequestParser passwordValidationRequestParser(
      AuthWorkflowProperties properties,
      PasswordValidationPurposePayloadDecoderRegistry decoders,
      ObjectMapper objectMapper) {
    var credential = properties.getLogin().getCredential();
    return new PasswordValidationRequestParser(
        decoders,
        objectMapper,
        credential.getPasswordMinLength(),
        credential.getPasswordMaxLength());
  }

  @Bean
  IssuePasswordValidationUseCase issuePasswordValidationUseCase(
      PasswordValidationThrottleService throttle,
      PasswordCheckPort passwords,
      PasswordValidationGrantService grants,
      RevokeAuthenticatedSessionUseCase revocation,
      FailureEventPort failures,
      OperationPayloadBindingPort bindings,
      PasswordValidationPurposePrecheckPolicyRegistry prechecks) {
    return new PasswordValidationIssuanceService(
        throttle, passwords, grants, revocation, failures, bindings, prechecks);
  }
}
