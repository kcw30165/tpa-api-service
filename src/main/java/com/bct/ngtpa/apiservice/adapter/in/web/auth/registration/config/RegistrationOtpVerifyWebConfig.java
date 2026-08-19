package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationOtpVerifyWebConfig {

  @Bean
  @ConditionalOnMissingBean
  public RegistrationOtpVerifyWebMapper registrationOtpVerifyWebMapper(
      WorkflowMutationResponseMapper workflowMutationResponseMapper) {
    return new RegistrationOtpVerifyWebMapper(workflowMutationResponseMapper);
  }
}
