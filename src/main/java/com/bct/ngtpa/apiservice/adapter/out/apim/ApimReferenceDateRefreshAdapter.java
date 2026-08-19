package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.FetchReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimReferenceDateRefreshAdapter implements ApimReferenceDateRefreshPort {
  private static final String API_NAME = "/ws/NGTPA/v1/TRPGetWebSysDate";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private final ApimExchangeExecutor apimExchangeExecutor;

  @Override
  public Mono<LocalDate> fetchReferenceDate(FetchReferenceDateCommand command) {
    var body = GetWebSysDateApimRequest.builder().accountEnv(command.accountEnv()).build();
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            body,
            GetWebSysDateApimRequest.class,
            GetWebSysDateApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return apimExchangeExecutor.postEnvelope(request).map(this::toReferenceDate);
  }

  private LocalDate toReferenceDate(ApimResponseEnvelope<GetWebSysDateApimDataItem> response) {
    var payload = response != null ? response.getResponse() : null;
    if (payload == null) throw invalid("APIM response payload is missing.");
    if (StringUtils.hasText(payload.getErrMessage())) throw invalid(payload.getErrMessage());
    if (CollectionUtils.isEmpty(payload.getData())) throw invalid("APIM response data is missing.");
    var first = payload.getData().getFirst();
    String sysDate = first == null ? null : first.getSysDate();
    if (!StringUtils.hasText(sysDate)) throw invalid("APIM sys-date is missing.");
    try {
      return LocalDate.parse(sysDate.trim(), DATE_FORMATTER);
    } catch (DateTimeParseException ex) {
      throw new ApimException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.APIM_RESPONSE_INVALID,
          "APIM sys-date must use dd/MM/yyyy format.",
          ex);
    }
  }

  private ApimException invalid(String message) {
    return new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, message);
  }
}
