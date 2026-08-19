package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config.TrpGetAccountListProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.TrpGetAccountListApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.TrpGetAccountListApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationCommand;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class SharedApimTrpGetAccountListTransport implements TrpGetAccountListTransport {
  private final ApimExchangeExecutor executor;
  private final TrpGetAccountListProperties properties;

  public SharedApimTrpGetAccountListTransport(
      ApimExchangeExecutor executor, TrpGetAccountListProperties properties) {
    this.executor = Objects.requireNonNull(executor);
    this.properties = Objects.requireNonNull(properties);
    properties.validate();
  }

  @Override
  public Mono<ApimResponseEnvelope<TrpGetAccountListApimDataItem>> post(
      LoginAccountAuthorizationCommand command) {
    Objects.requireNonNull(command);
    String path = properties.getPath().trim();
    var request =
        new TrpGetAccountListApimRequest(
            command.searchKey(),
            command.searchValue(),
            command.custId(),
            command.actorUserRole().toApimValue());
    var exchange =
        new ApimExchangeRequest<>(
            path,
            request,
            TrpGetAccountListApimRequest.class,
            TrpGetAccountListApimDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return executor.postEnvelope(exchange);
  }
}
