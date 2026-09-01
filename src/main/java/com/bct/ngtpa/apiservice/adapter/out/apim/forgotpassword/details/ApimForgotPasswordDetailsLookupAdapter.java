package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper.ForgotPasswordTrpSearchMemberMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimForgotPasswordDetailsLookupAdapter
    implements ForgotPasswordDetailsLookupPort {
  public static final String API_NAME = "/ws/NGTPA/v1/TRPSearchMember";
  private final ApimExchangeExecutor executor;
  private final ForgotPasswordTrpSearchMemberMapper mapper =
      new ForgotPasswordTrpSearchMemberMapper();

  public ApimForgotPasswordDetailsLookupAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public Mono<ForgotPasswordDetailsLookupResult> verify(
      ForgotPasswordDetailsLookupCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    ForgotPasswordTrpSearchMemberRequest body = mapper.toRequest(command);
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            body,
            ForgotPasswordTrpSearchMemberRequest.class,
            ForgotPasswordTrpSearchMemberDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return executor
        .postEnvelope(request)
        .map(
            envelope -> {
              if (envelope == null || envelope.getResponse() == null) {
                throw new ForgotPasswordDetailsDownstreamException(
                    ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR);
              }
              var response = envelope.getResponse();
              List<ForgotPasswordTrpSearchMemberDataItem> data =
                  response.getData() == null ? List.of() : List.copyOf(response.getData());
              return mapper.toResult(response.getErrMessage(), data, command.customerId());
            });
  }
}
