package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper.TrpSearchMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class TrpSearchMemberAdapter implements UsernameRecoveryLookupPort {
  public static final String API_NAME = "/ws/NGTPA/v1/TRPSearchMember";
  private final ApimExchangeExecutor executor;
  private final TrpSearchMemberApimMapper requestMapper = new TrpSearchMemberApimMapper();
  private final TrpSearchMemberApimResultMapper resultMapper =
      new TrpSearchMemberApimResultMapper();

  public TrpSearchMemberAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    TrpSearchMemberApimRequest body = requestMapper.toApimRequest(command);
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            body,
            TrpSearchMemberApimRequest.class,
            TrpSearchMemberApimDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return Mono.deferContextual(
            contextView -> {
              contextView.getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
              return executor.postEnvelope(request);
            })
        .map(this::toResult)
        .onErrorResume(
            TrpSearchMemberApimDownstreamException.class, e -> Mono.just(unknownOutcome()))
        .onErrorResume(ApimException.class, e -> Mono.just(unavailable()));
  }

  private UsernameRecoveryLookupResult toResult(
      ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope) {
    if (envelope == null || envelope.getResponse() == null) return unavailable();
    var response = envelope.getResponse();
    List<TrpSearchMemberApimDataItem> data =
        response.getData() == null ? List.of() : List.copyOf(response.getData());
    return resultMapper.toResult(response.getErrMessage(), data);
  }

  private static UsernameRecoveryLookupResult unavailable() {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.UNAVAILABLE, List.of());
  }

  private static UsernameRecoveryLookupResult unknownOutcome() {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME, List.of());
  }
}
