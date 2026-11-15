package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCnlCIMReqApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCnlCIMReqApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelNotCancellableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceCancelPort;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimInvestmentChoiceCancelAdapter implements InvestmentChoiceCancelPort {

  private static final String OPERATION = "TRPCnclCIMReq";
  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String EVENT = "investment-choice.cancel.downstream_failure";
  private static final String NOT_CANCELLABLE_CODE = "err.request.cannot.be.cancelled";
  private static final String NOT_CANCELLABLE_PUBLIC_CODE =
      "err.investment-choice.cancel.not-cancellable";
  private static final String UNAVAILABLE_PUBLIC_CODE = "err.investment-choice.cancel.unavailable";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEventPort;
  private final String path;

  @Autowired
  public ApimInvestmentChoiceCancelAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      InvestmentChoiceCancelApimProperties properties) {
    this(executor, failureEventPort, properties.getPath());
  }

  ApimInvestmentChoiceCancelAdapter(
      ApimExchangeExecutor executor, FailureEventPort failureEventPort, String path) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.failureEventPort =
        Objects.requireNonNull(failureEventPort, "failureEventPort must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
  }

  @Override
  public Mono<InvestmentChoiceCancellation> cancel(InvestmentChoiceCancelCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpCnlCIMReqApimRequest(
                      command.actorUserId(),
                      command.actorUserRole().toApimValue(),
                      command.policyNo(),
                      command.certNo(),
                      command.referenceNo());
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpCnlCIMReqApimRequest.class,
                      TrpCnlCIMReqApimDataItem.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.NON_RETRYABLE,
                      ApimResubmissionPolicy.NEVER,
                      ApimIncidentOwnership.TERMINAL_CALLER);
              return executor
                  .<TrpCnlCIMReqApimRequest, TrpCnlCIMReqApimDataItem>postEnvelope(exchange)
                  .map(response -> toCancellation(response, command));
            })
        .doOnEach(this::publishFailure)
        .onErrorMap(this::cancellationException);
  }

  private InvestmentChoiceCancellation toCancellation(
      ApimResponseEnvelope<TrpCnlCIMReqApimDataItem> envelope,
      InvestmentChoiceCancelCommand command) {
    if (envelope == null || envelope.getResponse() == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    var response = envelope.getResponse();
    String errMessage = response.getErrMessage();
    if (hasText(errMessage)) {
      throw new DownstreamErrorMessageException(errMessage);
    }
    var data = response.getData();
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    return new InvestmentChoiceCancellation(command.referenceNo());
  }

  private Throwable cancellationException(Throwable failure) {
    if (failure instanceof DownstreamErrorMessageException error
        && NOT_CANCELLABLE_CODE.equals(error.code())) {
      return new InvestmentChoiceCancelNotCancellableException();
    }
    if (failure instanceof InvestmentChoiceCancelUnavailableException
        || failure instanceof InvestmentChoiceCancelNotCancellableException) {
      return failure;
    }
    return new InvestmentChoiceCancelUnavailableException();
  }

  private void publishFailure(reactor.core.publisher.Signal<InvestmentChoiceCancellation> signal) {
    if (!signal.isOnError()) {
      return;
    }
    Throwable failure = signal.getThrowable();
    FailureOrigin origin;
    String internalCode = null;
    String publicCode = UNAVAILABLE_PUBLIC_CODE;
    int httpStatus = 503;
    String reason;
    if (failure instanceof DownstreamErrorMessageException error) {
      internalCode = error.code();
      if (NOT_CANCELLABLE_CODE.equals(error.code())) {
        origin = FailureOrigin.DOWNSTREAM_BUSINESS;
        publicCode = NOT_CANCELLABLE_PUBLIC_CODE;
        httpStatus = 422;
      } else {
        origin = FailureOrigin.DOWNSTREAM_CONTRACT;
      }
      reason = origin.name();
    } else if (failure instanceof InvestmentChoiceMalformedResponseException) {
      origin = FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      reason = "MALFORMED_RESPONSE";
    } else {
      ApimFailureCategory category = ApimFailureClassifier.classify(failure);
      origin = toFailureOrigin(category);
      reason = category.name();
    }
    String requestId =
        signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
    failureEventPort.publish(
        new FailureEvent(
            EVENT,
            requestId,
            CAPABILITY,
            OPERATION,
            origin,
            internalCode,
            publicCode,
            httpStatus,
            false,
            Map.of("reason", reason)));
  }

  private static FailureOrigin toFailureOrigin(ApimFailureCategory category) {
    return switch (category) {
      case DOWNSTREAM_CONTRACT -> FailureOrigin.DOWNSTREAM_CONTRACT;
      case DOWNSTREAM_INVALID_RESPONSE -> FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      case DOWNSTREAM_TRANSPORT -> FailureOrigin.DOWNSTREAM_TRANSPORT;
      case LOCAL_SYSTEM -> FailureOrigin.LOCAL_SYSTEM;
    };
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static final class DownstreamErrorMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    private DownstreamErrorMessageException(String code) {
      super("APIM returned an error message");
      this.code = Objects.requireNonNull(code);
    }

    private String code() {
      return code;
    }
  }
}
