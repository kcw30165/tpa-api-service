package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimRequest;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.exception.VestingScaleRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.VestingScalePort;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimVestingScaleAdapter implements VestingScalePort {

  private static final String OPERATION = "TRPGetVestingScale";
  private static final String CAPABILITY = "VESTING_SCALE";
  private static final String EVENT = "vesting-scale.retrieval.downstream_failure";
  private static final String PUBLIC_CODE = "err.vesting-scale.retrieval.unavailable";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEventPort;
  private final ObjectMapper objectMapper;
  private final String path;
  private final TrpGetVestingScaleApimMapper mapper = new TrpGetVestingScaleApimMapper();

  @Autowired
  public ApimVestingScaleAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      ObjectMapper objectMapper,
      VestingScaleApimProperties properties) {
    this(executor, failureEventPort, objectMapper, properties.getPath());
  }

  ApimVestingScaleAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      ObjectMapper objectMapper,
      String path) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.failureEventPort =
        Objects.requireNonNull(failureEventPort, "failureEventPort must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
  }

  @Override
  public Mono<VestingScaleData> load(VestingScaleCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpGetVestingScaleApimRequest(
                      command.actorUserId(),
                      command.actorUserRole().toApimValue(),
                      command.policyNo(),
                      command.certNo());
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpGetVestingScaleApimRequest.class,
                      JsonNode.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.SAFE_BUSINESS_CALL,
                      ApimResubmissionPolicy.ALLOW_RECOVERY,
                      ApimIncidentOwnership.TERMINAL_CALLER);
              return executor
                  .<TrpGetVestingScaleApimRequest, JsonNode>postEnvelope(exchange)
                  .switchIfEmpty(
                      Mono.error(
                          malformed(
                              TrpGetVestingScaleApimDataItem.RESPONSE_KEY,
                              VestingScaleMalformedResponseException.MalformedReason
                                  .MISSING_REQUIRED_FIELD)))
                  .map(this::mapEnvelope);
            })
        .doOnEach(this::publishFailure)
        .onErrorMap(
            failure ->
                failure instanceof VestingScaleRetrievalException
                    ? failure
                    : VestingScaleRetrievalException.systemFailure());
  }

  private VestingScaleData mapEnvelope(ApimResponseEnvelope<JsonNode> envelope) {
    if (envelope == null || envelope.getResponse() == null) {
      throw malformed(
          TrpGetVestingScaleApimDataItem.RESPONSE_KEY,
          VestingScaleMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    var response = envelope.getResponse();
    String errMessage = response.getErrMessage();
    if (hasText(errMessage)) {
      throw new DownstreamErrorMessageException(reasonFor(errMessage));
    }
    List<JsonNode> data = response.getData();
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw malformed(
          TrpGetVestingScaleApimDataItem.DATA_KEY,
          VestingScaleMalformedResponseException.MalformedReason.INVALID_CARDINALITY);
    }
    try {
      TrpGetVestingScaleApimDataItem item =
          objectMapper.treeToValue(data.getFirst(), TrpGetVestingScaleApimDataItem.class);
      return mapper.map(item);
    } catch (JsonProcessingException | IllegalArgumentException failure) {
      throw malformed(
          jacksonPath(failure),
          VestingScaleMalformedResponseException.MalformedReason.INVALID_TYPE);
    }
  }

  private static String jacksonPath(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < 16; depth++) {
      if (current instanceof JsonMappingException mapping) {
        String path = formatPath(mapping);
        if (path != null) {
          return path;
        }
      }
      Throwable cause = current.getCause();
      current = cause == current ? null : cause;
    }
    return TrpGetVestingScaleApimDataItem.DATA_KEY + "[0]";
  }

  private static String formatPath(JsonMappingException mapping) {
    List<JsonMappingException.Reference> references = mapping.getPath();
    StringBuilder path = new StringBuilder();
    for (JsonMappingException.Reference reference : references) {
      if (reference.getFieldName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(reference.getFieldName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.isEmpty() ? null : path.toString();
  }

  private static VestingScaleMalformedResponseException malformed(
      String fieldPath, VestingScaleMalformedResponseException.MalformedReason reason) {
    return new VestingScaleMalformedResponseException(fieldPath, reason);
  }

  private void publishFailure(reactor.core.publisher.Signal<VestingScaleData> signal) {
    if (!signal.isOnError()) {
      return;
    }
    Throwable failure = signal.getThrowable();
    FailureOrigin origin;
    String reason = null;
    Map<String, Object> dimensions;
    if (failure instanceof DownstreamErrorMessageException error) {
      origin = FailureOrigin.DOWNSTREAM_CONTRACT;
      reason = error.reason().name();
    } else if (failure instanceof VestingScaleMalformedResponseException) {
      origin = FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      reason = VestingScaleFailureReason.MALFORMED_RESPONSE.name();
    } else {
      origin = toFailureOrigin(ApimFailureClassifier.classify(failure));
    }
    if (failure instanceof VestingScaleMalformedResponseException malformed
        && malformed.malformedField() != null
        && malformed.malformedReason() != null) {
      dimensions =
          Map.of(
              "reason", reason,
              "malformedField", malformed.malformedField(),
              "malformedReason", malformed.malformedReason().name());
    } else {
      dimensions = Map.of("reason", reason == null ? origin.name() : reason);
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
            null,
            PUBLIC_CODE,
            503,
            true,
            dimensions));
  }

  private static VestingScaleFailureReason reasonFor(String errMessage) {
    return switch (errMessage) {
      case "err.tpa.login.user.id.invalid",
          "err.tpa.cert.no.not.found",
          "err.tpa.cert.no.required" ->
          VestingScaleFailureReason.ACCOUNT_CONTEXT_MISMATCH;
      default -> VestingScaleFailureReason.UNMAPPED_ERROR_CODE;
    };
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

    private final VestingScaleFailureReason reason;

    private DownstreamErrorMessageException(VestingScaleFailureReason reason) {
      super("APIM returned an error message");
      this.reason = reason;
    }

    private VestingScaleFailureReason reason() {
      return reason;
    }
  }
}
