package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import reactor.core.publisher.Mono;

public interface PasswordValidationPurposePrecheckPolicy<
    T extends PasswordValidationPurposePayload> {

  PasswordValidationPurpose purpose();

  Class<T> payloadType();

  Mono<PasswordValidationPrecheckResult> precheck(
      T payload, PasswordValidationPrecheckContext context);
}
