package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckCommand;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.bct.ngtpa.apiservice.application.port.out.PasswordCheckPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ApimPasswordValidationAdapter implements PasswordCheckPort {
  private static final String PATH = "/ws/NGTPA/v1/TRPPwdValidation";
  private final ApimExchangeExecutor executor;

  public ApimPasswordValidationAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor);
  }

  @Override
  public Mono<PasswordCheckResult> check(PasswordCheckCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(() -> executor.postEnvelope(new ApimExchangeRequest<>(
            PATH,
            new TrpPwdValidationRequest(command.customerId(), command.password()),
            TrpPwdValidationRequest.class,
            TrpPwdValidationDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.NON_RETRYABLE,
            ApimResubmissionPolicy.NEVER)))
        .map(this::interpret)
        .defaultIfEmpty(PasswordCheckResult.UNAVAILABLE)
        .onErrorReturn(PasswordCheckResult.UNAVAILABLE);
  }

  private PasswordCheckResult interpret(ApimResponseEnvelope<TrpPwdValidationDataItem> envelope) {
    if (envelope.getResponse() == null || envelope.getResponse().getErrMessage() == null) {
      return PasswordCheckResult.UNAVAILABLE;
    }
    var response = envelope.getResponse();
    String error = response.getErrMessage();
    if (!error.isEmpty()) {
      return switch (error) {
        case "err.tpa.account.locked" -> PasswordCheckResult.ACCOUNT_LOCKED;
        case "err.tpa.cust.id.not.registered" -> PasswordCheckResult.CUSTOMER_NOT_REGISTERED;
        default -> PasswordCheckResult.UNAVAILABLE;
      };
    }
    var data = response.getData();
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      return PasswordCheckResult.UNAVAILABLE;
    }
    var success = data.getFirst().success();
    if (success == null || !success.isBoolean()) {
      return PasswordCheckResult.UNAVAILABLE;
    }
    return success.booleanValue() ? PasswordCheckResult.CORRECT : PasswordCheckResult.INCORRECT;
  }
}
