package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config.TrpGetAccountListProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import java.util.*;
import reactor.core.publisher.Mono;

public final class TrpGetAccountListAdapter implements LoginAccountAuthorizationPort {
  private final TrpGetAccountListTransport transport;
  private final TrpGetAccountListContractMapper mapper;
  private final Map<String, String> errorMappings;

  public TrpGetAccountListAdapter(
      TrpGetAccountListTransport transport,
      TrpGetAccountListContractMapper mapper,
      TrpGetAccountListProperties properties) {
    this.transport = Objects.requireNonNull(transport);
    this.mapper = Objects.requireNonNull(mapper);
    Objects.requireNonNull(properties).validate();
    this.errorMappings = Map.copyOf(properties.getErrorMappings());
  }

  public Mono<LoginAccountAuthorizationResult> getAccounts(
      LoginAccountAuthorizationCommand command) {
    Objects.requireNonNull(command);
    return transport
        .post(command)
        .map(this::mapEnvelope)
        .onErrorReturn(LoginAccountAuthorizationResult.unavailable());
  }

  private LoginAccountAuthorizationResult mapEnvelope(
      ApimResponseEnvelope<TrpGetAccountListApimDataItem> envelope) {
    if (envelope == null || envelope.getResponse() == null)
      return LoginAccountAuthorizationResult.unavailable();
    ApimResponseBody<TrpGetAccountListApimDataItem> response = envelope.getResponse();
    String error = normalize(response.getErrMessage());
    List<TrpGetAccountListApimDataItem> data =
        response.getData() == null ? List.of() : List.copyOf(response.getData());
    if (error != null) {
      return LoginAccountAuthorizationResult.unavailable();
    }
    if (data.isEmpty()) return LoginAccountAuthorizationResult.unavailable();
    try {
      return LoginAccountAuthorizationResult.available(
          data.stream().map(mapper::toRecord).toList());
    } catch (RuntimeException exception) {
      return LoginAccountAuthorizationResult.unavailable();
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
  }
}
