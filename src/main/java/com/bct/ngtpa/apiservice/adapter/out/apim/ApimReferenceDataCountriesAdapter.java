package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimReferenceDataCountriesAdapter implements ApimReferenceDataCountriesPort {
  private static final String API_NAME = "/ws/NGTPA/v1/TRPGetCountryList";
  private final ApimExchangeExecutor apimExchangeExecutor;

  @Override
  @LogExecution(value = "apim.fetchCountryList", logArgs = true)
  public Mono<List<ReferenceDataCountryItem>> fetchCountryList(
      GetReferenceDataCountriesCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var exchange =
        new ApimExchangeRequest<>(
            API_NAME,
            newRequest(),
            GetCountryListApimRequest.class,
            GetCountryListDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return apimExchangeExecutor.postEnvelope(exchange).map(this::toCountryItems);
  }

  private GetCountryListApimRequest newRequest() {
    return new GetCountryListApimRequest();
  }

  private List<ReferenceDataCountryItem> toCountryItems(
      ApimResponseEnvelope<GetCountryListDataItem> response) {
    var dataItems = ApimResponseValidator.requireSuccessData(response);
    if (dataItems.isEmpty()) return List.of();
    return dataItems.stream()
        .filter(Objects::nonNull)
        .map(
            item ->
                new ReferenceDataCountryItem(
                    item.getCountryCode(),
                    item.getCountryNameEng(),
                    item.getCountryNameChi(),
                    item.getCallingCode()))
        .toList();
  }
}
