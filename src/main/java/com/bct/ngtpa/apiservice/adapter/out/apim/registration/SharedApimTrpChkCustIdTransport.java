package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/** Business-neutral typed transport shared by TRPChkCustId callers. */
public final class SharedApimTrpChkCustIdTransport implements TrpChkCustIdTransport {
  public static final String API_NAME = "/ws/NGTPA/v1/TRPChkCustId";
  private final ApimExchangeExecutor executor;

  public SharedApimTrpChkCustIdTransport(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public Mono<TrpChkCustIdApimEnvelope> post(TrpChkCustIdTransportCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            command.request(),
            TrpChkCustIdApimRequest.class,
            TrpChkCustIdApimDataItem.class,
            ApimDatabaseScope.NON_ACCOUNT,
            new ApimCredentialResolutionContext(command.actorUserRole(), null),
            ApimRetryEligibility.SAFE_BUSINESS_CALL,
            com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy.ALLOW_RECOVERY,
            command.incidentOwnership());
    return executor.postEnvelope(request).map(this::toEnvelope);
  }

  private TrpChkCustIdApimEnvelope toEnvelope(
      ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
    if (envelope == null || envelope.getResponse() == null) {
      throw new ApimException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.APIM_RESPONSE_INVALID,
          "TRPChkCustId response envelope is missing response");
    }
    String errMessage = envelope.getResponse().getErrMessage();
    List<TrpChkCustIdApimDataItem> data = envelope.getResponse().getData();
    boolean err = errMessage != null && !errMessage.isBlank();
    boolean hasData = data != null && !data.isEmpty();
    if (!err && !hasData) {
      throw new ApimException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.APIM_RESPONSE_INVALID,
          "TRPChkCustId returned an invalid/malformed response");
    }
    if (hasData
        && data.stream()
            .noneMatch(
                item -> item != null && (item.customerId() != null || item.isExist() != null))) {
      throw new ApimException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.APIM_RESPONSE_INVALID,
          "TRPChkCustId returned data items but they are malformed");
    }
    return new TrpChkCustIdApimEnvelope(errMessage, data);
  }
}
