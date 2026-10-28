package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceCommand;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceResult;
import reactor.core.publisher.Mono;

public interface IssuePasswordValidationUseCase {
  Mono<PasswordValidationIssuanceResult> issue(PasswordValidationIssuanceCommand command);
}
