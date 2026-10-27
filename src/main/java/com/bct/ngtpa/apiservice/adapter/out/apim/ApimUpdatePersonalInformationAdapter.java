package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.service.personalinformation.PersonalInformationUpdateOutcomeClassifier;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApimUpdatePersonalInformationAdapter implements ApimUpdatePersonalInformationPort {
  private static final String API_NAME = "/ws/NGTPA/v1/TRPUpdMemberInfo";
  private final ApimExchangeExecutor executor;
  private final AccountIdentifierFormatter formatter;

  @Override
  @LogExecution(value = "apim.updatePersonalInformation", logArgs = true)
  public Mono<List<UpdatePersonalInformationResult>> updateMemberInfo(
      UpdateMemberInfoCommand command) {
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            toApimRequest(command),
            UpdateMemberInfoApimRequest.class,
            UpdateMemberInfoApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.userRole(), command.accountEnv()),
            ApimRetryEligibility.IDEMPOTENT_MUTATION);
    return Mono.defer(() -> executor.postEnvelope(request))
        .onErrorMap(this::isTransportFailure, error -> unavailable("TRANSPORT_FAILURE"))
        .switchIfEmpty(Mono.defer(() -> Mono.error(unavailable("EMPTY_RESPONSE"))))
        .map(response -> toResults(response, command));
  }

  private UpdateMemberInfoApimRequest toApimRequest(UpdateMemberInfoCommand command) {
    return UpdateMemberInfoApimRequest.builder()
        .policyNo(command.policyNo())
        .certNo(command.certNo())
        .applyAll(Boolean.TRUE.equals(command.applyToAllAccounts()))
        .updateFields(command.updateFields())
        .loginUserId(command.userId())
        .loginUserRole(command.userRole().toApimValue())
        .build();
  }

  private List<UpdatePersonalInformationResult> toResults(
      ApimResponseEnvelope<UpdateMemberInfoApimDataItem> response,
      UpdateMemberInfoCommand command) {
    final List<UpdateMemberInfoApimDataItem> dataItems;
    try {
      dataItems = ApimResponseValidator.requireSuccessData(response);
    } catch (ApimException error) {
      throw unavailable("INVALID_ENVELOPE");
    }
    var results = dataItems.stream().map(this::toResult).toList();
    var classification =
        new PersonalInformationUpdateOutcomeClassifier(formatter).classify(command, results);
    if (classification.outcome() == PersonalInformationUpdateOutcomeClassifier.Outcome.SYSTEM_ERROR) {
      throw unavailable(classification.failure().name());
    }
    if (classification.discardedOtherAccounts() > 0) {
      log.warn(
          "event=personal-information.update.other-accounts-dropped count={}",
          classification.discardedOtherAccounts());
    }
    return classification.rows();
  }

  private UpdatePersonalInformationResult toResult(UpdateMemberInfoApimDataItem item) {
    if (item == null) return null;
    return new UpdatePersonalInformationResult(
        false,
        item.isSuccess(),
        item.getPolicyNo(),
        item.getCertNo(),
        item.getEnv(),
        item.getRefNo(),
        item.getSubmitDate(),
        item.getSubmitTime(),
        toErrors(item));
  }

  private boolean isTransportFailure(Throwable error) {
    if (error instanceof ApimException
        || error instanceof WebClientRequestException
        || error instanceof TimeoutException
        || error instanceof IOException) return true;
    return error.getCause() != null
        && error.getCause() != error
        && isTransportFailure(error.getCause());
  }

  private PersonalInformationUpdateException unavailable(String reason) {
    log.error("event=personal-information.update.unavailable reason={}", reason);
    return PersonalInformationUpdateException.systemFailure();
  }

  private List<UpdatePersonalInformationError> toErrors(UpdateMemberInfoApimDataItem item) {
    if (item == null || item.getErrors() == null || item.getErrors().isEmpty()) return List.of();
    if (item.getErrors().stream().anyMatch(java.util.Objects::isNull)) {
      throw unavailable("INVALID_ERRORS");
    }
    return item.getErrors().stream()
        .map(
            error ->
                UpdatePersonalInformationError.fromPipeSeparatedFields(
                    error.getType(), error.getFields(), error.getCode()))
        .toList();
  }
}
