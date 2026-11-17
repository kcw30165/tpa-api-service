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
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCIMReqApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCIMReqApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCIMReqApimRequest.CimInstruction;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRow;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionRejectedException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionSamePendingException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnknownOutcomeException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceInstructionPort;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimInvestmentChoiceInstructionAdapter
    implements InvestmentChoiceInstructionPort {

  private static final String OPERATION = "TRPCIMReq";
  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String EVENT = "investment-choice.submission.downstream_failure";
  private static final String SAME_SOURCE_PENDING_CODE = "err.tpa.same.src.cim.exist";
  private static final String SAME_SOURCE_PENDING_PUBLIC_CODE =
      ErrorCodes.INVESTMENT_CHOICE_SUBMISSION_SAME_SOURCE_PENDING;
  private static final String PUBLIC_CODE = "err.investment-choice.submission.unavailable";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEventPort;
  private final String path;

  @Autowired
  public ApimInvestmentChoiceInstructionAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      InvestmentChoiceInstructionApimProperties properties) {
    this(executor, failureEventPort, properties.getPath());
  }

  ApimInvestmentChoiceInstructionAdapter(
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
  public Mono<InvestmentChoiceSubmission> submit(InvestmentChoiceInstructionCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpCIMReqApimRequest(
                      command.actorUserId(),
                      command.actorUserRole().toApimValue(),
                      command.policyNo(),
                      command.certNo(),
                      toWireInstructions(command.rows()));
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpCIMReqApimRequest.class,
                      TrpCIMReqApimDataItem.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.NON_RETRYABLE,
                      ApimResubmissionPolicy.NEVER,
                      ApimIncidentOwnership.TERMINAL_CALLER);
              return executor
                  .<TrpCIMReqApimRequest, TrpCIMReqApimDataItem>postEnvelope(exchange)
                  .map(this::interpret);
            })
        .doOnEach(this::publishFailure)
        .onErrorMap(this::submissionException);
  }

  private List<CimInstruction> toWireInstructions(List<InvestmentChoiceInstructionRow> rows) {
    return rows.stream()
        .map(row -> new CimInstruction(row.switchSource(), row.fundCode(), row.percentage()))
        .toList();
  }

  private InvestmentChoiceSubmission interpret(
      ApimResponseEnvelope<TrpCIMReqApimDataItem> envelope) {
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
    var item = data.getFirst();
    if (!hasText(item.referenceNo())) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    return new InvestmentChoiceSubmission(
        item.referenceNo(),
        TrpGetCIMDtlApimMapper.parseDate(item.submitDate()),
        TrpGetCIMDtlApimMapper.parseTime(item.submitTime()));
  }

  private Throwable submissionException(Throwable failure) {
    if (failure instanceof DownstreamErrorMessageException error) {
      if (SAME_SOURCE_PENDING_CODE.equals(error.code())) {
        return new InvestmentChoiceSubmissionSamePendingException();
      }
      return new InvestmentChoiceSubmissionRejectedException();
    }
    if (failure instanceof InvestmentChoiceSubmissionSamePendingException) {
      return failure;
    }
    return new InvestmentChoiceSubmissionUnknownOutcomeException();
  }

  private void publishFailure(reactor.core.publisher.Signal<InvestmentChoiceSubmission> signal) {
    if (!signal.isOnError()) {
      return;
    }
    Throwable failure = signal.getThrowable();
    FailureOrigin origin;
    String internalCode = null;
    String publicCode = PUBLIC_CODE;
    int httpStatus = 503;
    if (failure instanceof DownstreamErrorMessageException error) {
      internalCode = error.code();
      if (SAME_SOURCE_PENDING_CODE.equals(error.code())) {
        origin = FailureOrigin.DOWNSTREAM_BUSINESS;
        publicCode = SAME_SOURCE_PENDING_PUBLIC_CODE;
        httpStatus = 422;
      } else {
        origin = FailureOrigin.DOWNSTREAM_CONTRACT;
      }
    } else if (failure instanceof InvestmentChoiceMalformedResponseException) {
      origin = FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
    } else {
      origin = toFailureOrigin(ApimFailureClassifier.classify(failure));
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
            Map.of("reason", origin.name())));
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
