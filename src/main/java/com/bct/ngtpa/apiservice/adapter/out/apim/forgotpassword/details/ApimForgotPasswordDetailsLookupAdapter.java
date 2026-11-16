package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper.ForgotPasswordTrpSearchMemberMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
            ApimRetryEligibility.SAFE_BUSINESS_CALL,
            ApimResubmissionPolicy.ALLOW_RECOVERY,
            ApimIncidentOwnership.TERMINAL_CALLER);
    return executor
        .postEnvelope(request)
        .map(
            envelope -> {
              if (envelope == null || envelope.getResponse() == null) {
                throw new ForgotPasswordDetailsDownstreamException(
                    ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR);
              }
              var response = envelope.getResponse();
              List<ForgotPasswordTrpSearchMemberDataItem> data = response.getData();
              return mapper.toResult(response.getErrMessage(), data, command.customerId());
            });
  }

  @Override
  public FailureOrigin classifyFailure(Throwable failure) {
    ForgotPasswordDetailsDownstreamException downstream =
        findCause(failure, ForgotPasswordDetailsDownstreamException.class);
    if (downstream != null) {
      return switch (downstream.reason()) {
        case MALFORMED_DATA, AMBIGUOUS_DATA -> FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
        case EMPTY_DATA, CONTACT_MISMATCH -> FailureOrigin.DOWNSTREAM_BUSINESS;
        case UNEXPECTED_ERROR -> FailureOrigin.DOWNSTREAM_CONTRACT;
      };
    }

    ApimException apim = findCause(failure, ApimException.class);
    if (apim != null && ErrorCodes.APIM_RESPONSE_INVALID.equals(apim.getErrorCode())) {
      return FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
    }
    if (isUnavailable(failure)) {
      return FailureOrigin.DOWNSTREAM_TRANSPORT;
    }
    return switch (ApimFailureClassifier.classify(failure)) {
      case DOWNSTREAM_CONTRACT -> FailureOrigin.DOWNSTREAM_CONTRACT;
      case DOWNSTREAM_INVALID_RESPONSE -> FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      case DOWNSTREAM_TRANSPORT -> FailureOrigin.DOWNSTREAM_TRANSPORT;
      case LOCAL_SYSTEM -> FailureOrigin.LOCAL_SYSTEM;
    };
  }

  @Override
  public boolean isUnavailableFailure(Throwable failure) {
    return isUnavailable(failure);
  }

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    while (current != null && visited.add(current)) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }

  private static boolean isUnavailable(Throwable failure) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    while (current != null && visited.add(current)) {
      if (current instanceof ApimException apim
          && !ErrorCodes.APIM_RESPONSE_INVALID.equals(apim.getErrorCode())
          && (apim.getStatusCode().value() == 503
              || apim.getStatusCode().value() == 504
              || ErrorCodes.APIM_SERVICE_UNAVAILABLE.equals(apim.getErrorCode())
              || ErrorCodes.APIM_TIMEOUT.equals(apim.getErrorCode()))) {
        return true;
      }
      if (current instanceof ConnectException
          || current instanceof WebClientRequestException
          || current instanceof InterruptedIOException
          || current instanceof SocketException
          || current instanceof UnknownHostException
          || current instanceof TimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
