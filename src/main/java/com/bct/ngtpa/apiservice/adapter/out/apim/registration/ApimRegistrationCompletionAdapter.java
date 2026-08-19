package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpRegMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpRegMemberApimResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationCompletionAdapter implements RegistrationCompletionPort {
  static final String API_NAME = "/ws/NGTPA/v1/TRPRegMember";
  private final ApimExchangeExecutor executor;
  private final TrpRegMemberApimMapper mapper = new TrpRegMemberApimMapper();
  private final TrpRegMemberApimResultMapper resultMapper = new TrpRegMemberApimResultMapper();

  public ApimRegistrationCompletionAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  @LogExecution(value = "apim.trpRegMember", logArgs = false)
  public Mono<RegistrationCompletionResult> complete(RegistrationCompletionCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    TrpRegMemberApimRequest requestBody = mapper.toRequest(command);
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            requestBody,
            TrpRegMemberApimRequest.class,
            TrpRegMemberApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.NON_RETRYABLE);
    return executor
        .postEnvelope(request)
        .map(
            envelope ->
                new TrpRegMemberApimEnvelope(
                    envelope.getResponse().getErrMessage(), envelope.getResponse().getData()))
        .map(resultMapper::toResult);
  }
}
