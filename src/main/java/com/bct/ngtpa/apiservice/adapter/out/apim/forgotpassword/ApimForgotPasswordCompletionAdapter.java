package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCryptoFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionTechnicalFailure;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimForgotPasswordCompletionAdapter implements ForgotPasswordCompletionPort {
  static final String API_NAME = "/ws/NGTPA/v1/TRPResetPassword";
  private final ApimExchangeExecutor executor;
  private final TrpResetPasswordApimMapper mapper = new TrpResetPasswordApimMapper();
  private final TrpResetPasswordApimResultMapper resultMapper =
      new TrpResetPasswordApimResultMapper();

  public ApimForgotPasswordCompletionAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  @LogExecution(value = "apim.trpResetPassword", logArgs = false)
  public Mono<ForgotPasswordCompletionResult> complete(ForgotPasswordCompletionCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    TrpResetPasswordApimRequest request = mapper.toRequest(command);
    var exchange =
        new ApimExchangeRequest<>(
            API_NAME,
            request,
            TrpResetPasswordApimRequest.class,
            TrpResetPasswordApimDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.NON_RETRYABLE,
            ApimResubmissionPolicy.NEVER,
            ApimIncidentOwnership.TERMINAL_CALLER);
    return executor
        .postEnvelope(exchange)
        .map(this::toEnvelope)
        .map(
            e -> {
              try {
                return resultMapper.toResult(e, command.customerId());
              } catch (RuntimeException failure) {
                throw new ForgotPasswordCompletionTechnicalFailure(
                    FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, "apim-reset-response");
              }
            })
        .onErrorMap(this::classifiedFailure);
  }

  private Throwable classifiedFailure(Throwable failure) {
    if (failure instanceof ForgotPasswordCompletionTechnicalFailure) {
      return failure;
    }
    if (ApimCryptoFailureClassifier.isCryptoFailure(failure)) {
      return new ForgotPasswordCompletionTechnicalFailure(
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, "apim-reset-response");
    }
    FailureOrigin origin =
        switch (ApimFailureClassifier.classify(failure)) {
      case DOWNSTREAM_CONTRACT ->
          FailureOrigin.DOWNSTREAM_CONTRACT;
      case DOWNSTREAM_INVALID_RESPONSE ->
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      case DOWNSTREAM_TRANSPORT ->
          FailureOrigin.DOWNSTREAM_TRANSPORT;
      case LOCAL_SYSTEM -> FailureOrigin.LOCAL_SYSTEM;
    };
    return new ForgotPasswordCompletionTechnicalFailure(origin, "apim-reset");
  }

  private TrpResetPasswordApimEnvelope toEnvelope(
      ApimResponseEnvelope<TrpResetPasswordApimDataItem> envelope) {
    if (envelope == null || envelope.getResponse() == null)
      throw new ApimException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.APIM_RESPONSE_INVALID,
          "TRPResetPassword response envelope is missing response");
    return new TrpResetPasswordApimEnvelope(
        envelope.getResponse().getErrMessage(),
        envelope.getResponse().getData() == null ? List.of() : envelope.getResponse().getData());
  }
}
