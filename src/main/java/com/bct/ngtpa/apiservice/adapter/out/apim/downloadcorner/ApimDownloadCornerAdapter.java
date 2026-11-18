package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerData;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.DownloadCornerRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.DownloadCornerPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
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
public final class ApimDownloadCornerAdapter implements DownloadCornerPort {

  private static final String OPERATION = "TRPGetDownloadCorner";
  private static final String CAPABILITY = "DOWNLOAD_CORNER";
  private static final String EVENT = "download-corner.retrieval.downstream_failure";
  private static final String PUBLIC_CODE = "err.download-corner.retrieval.unavailable";
  private static final String RESPONSE_KEY = "response";
  private static final String DATA_KEY = "response.data";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEventPort;
  private final ObjectMapper objectMapper;
  private final String path;
  private final TrpGetDownloadCornerApimMapper mapper = new TrpGetDownloadCornerApimMapper();

  @Autowired
  public ApimDownloadCornerAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      ObjectMapper objectMapper,
      DownloadCornerApimProperties properties) {
    this(executor, failureEventPort, objectMapper, properties.getPath());
  }

  ApimDownloadCornerAdapter(
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
  public Mono<DownloadCornerData> load(DownloadCornerCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpGetDownloadCornerApimRequest(
                      command.actorUserId(),
                      command.actorUserRole().toApimValue(),
                      command.policyNo(),
                      command.certNo());
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpGetDownloadCornerApimRequest.class,
                      JsonNode.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.SAFE_BUSINESS_CALL,
                      ApimResubmissionPolicy.ALLOW_RECOVERY,
                      ApimIncidentOwnership.TERMINAL_CALLER);
              return executor
                  .<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(exchange)
                  .switchIfEmpty(
                      Mono.error(
                          malformed(
                              RESPONSE_KEY,
                              DownloadCornerMalformedResponseException.MalformedReason
                                  .MISSING_REQUIRED_FIELD)))
                  .map(this::mapEnvelope);
            })
        .doOnEach(this::publishFailure)
        .onErrorMap(
            failure ->
                failure instanceof DownloadCornerRetrievalException
                    ? failure
                    : DownloadCornerRetrievalException.systemFailure());
  }

  private DownloadCornerData mapEnvelope(ApimResponseEnvelope<JsonNode> envelope) {
    if (envelope == null || envelope.getResponse() == null) {
      throw malformed(
          RESPONSE_KEY,
          DownloadCornerMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    var response = envelope.getResponse();
    String errMessage = response.getErrMessage();
    if (hasText(errMessage)) {
      throw new DownstreamErrorMessageException(errMessage, reasonFor(errMessage));
    }
    List<JsonNode> data = response.getData();
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw malformed(
          DATA_KEY, DownloadCornerMalformedResponseException.MalformedReason.INVALID_CARDINALITY);
    }
    try {
      TrpGetDownloadCornerApimDataItem item =
          objectMapper.treeToValue(data.getFirst(), TrpGetDownloadCornerApimDataItem.class);
      return mapper.map(item);
    } catch (JsonProcessingException | IllegalArgumentException failure) {
      throw malformed(
          fullDataPath(jacksonPath(failure)),
          DownloadCornerMalformedResponseException.MalformedReason.INVALID_TYPE);
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
    return "response.data[0]";
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

  private static String fullDataPath(String fieldPath) {
    return fieldPath.startsWith("response.data[0]") ? fieldPath : "response.data[0]." + fieldPath;
  }

  private static DownloadCornerMalformedResponseException malformed(
      String fieldPath, DownloadCornerMalformedResponseException.MalformedReason reason) {
    return new DownloadCornerMalformedResponseException(fieldPath, reason);
  }

  private void publishFailure(reactor.core.publisher.Signal<DownloadCornerData> signal) {
    if (!signal.isOnError()) {
      return;
    }
    Throwable failure = signal.getThrowable();
    FailureOrigin origin;
    String internalCode = null;
    String reason = null;
    Map<String, Object> dimensions;
    if (failure instanceof DownstreamErrorMessageException error) {
      origin = FailureOrigin.DOWNSTREAM_CONTRACT;
      internalCode = error.code();
      reason = error.reason().name();
    } else if (failure instanceof DownloadCornerMalformedResponseException) {
      origin = FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      reason = DownloadCornerFailureReason.MALFORMED_RESPONSE.name();
    } else {
      origin = toFailureOrigin(ApimFailureClassifier.classify(failure));
    }
    if (failure instanceof DownloadCornerMalformedResponseException malformed
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
            internalCode,
            PUBLIC_CODE,
            503,
            true,
            dimensions));
  }

  private static DownloadCornerFailureReason reasonFor(String errMessage) {
    return switch (errMessage) {
      case "err.tpa.login.user.id.invalid",
          "err.tpa.cert.no.not.found",
          "err.tpa.cert.no.required" ->
          DownloadCornerFailureReason.ACCOUNT_CONTEXT_MISMATCH;
      default -> DownloadCornerFailureReason.UNMAPPED_ERROR_CODE;
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

    private final String code;
    private final DownloadCornerFailureReason reason;

    private DownstreamErrorMessageException(String code, DownloadCornerFailureReason reason) {
      super("APIM returned an error message");
      this.code = Objects.requireNonNull(code);
      this.reason = reason;
    }

    private String code() {
      return code;
    }

    private DownloadCornerFailureReason reason() {
      return reason;
    }
  }
}
