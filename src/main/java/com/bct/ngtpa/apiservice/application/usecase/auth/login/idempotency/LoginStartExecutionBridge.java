package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Converts typed Login Start results to transient executor responses and safe replay snapshots. */
public final class LoginStartExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final LoginStartReplayCodec replayCodec;

  public LoginStartExecutionBridge(TransientJsonPort json, LoginStartReplayCodec replayCodec) {
    this.json = Objects.requireNonNull(json);
    this.replayCodec = Objects.requireNonNull(replayCodec);
  }

  public HydratedIdempotentResponse toTransientResponse(StartLoginResult result) {
    Objects.requireNonNull(result, "Login Start result must not be null");
    if (result.status() == StartLoginStatus.COMPLETED_REPLAY) {
      throw new IllegalArgumentException("Login Start lifecycle replay is not a public result");
    }
    try {
      return new HydratedIdempotentResponse(
          transientHttpStatus(result.status()), MEDIA_TYPE, json.write(toWire(result)), Map.of());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login Start transient response encoding failed");
    }
  }

  public StartLoginResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Login Start transient response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Login Start transient media type is invalid");
    }
    try {
      TransientWire wire = json.read(response.serializedBody(), TransientWire.class);
      StartLoginStatus status = StartLoginStatus.valueOf(wire.status());
      return new StartLoginResult(
          status,
          wire.authFlowSessionId(),
          wire.csrfToken(),
          wire.expiresInSeconds(),
          wire.destinations() == null ? List.of() : wire.destinations(),
          wire.failCount(),
          wire.replayCommitRef());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login Start transient response decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return replayCodec.capture(fromTransientResponse(response), response);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return replayCodec.hydrate(snapshot).map(this::toTransientResponse);
  }

  private TransientWire toWire(StartLoginResult result) {
    return new TransientWire(
        result.status().name(),
        result.authFlowSessionId(),
        result.csrfToken(),
        result.expiresInSeconds(),
        result.destinations(),
        result.failCount(),
        result.replayCommitRef());
  }

  private int transientHttpStatus(StartLoginStatus status) {
    return LoginStartReplayCodec.httpStatus(status);
  }

  private record TransientWire(
      String status,
      String authFlowSessionId,
      String csrfToken,
      Integer expiresInSeconds,
      List<LoginDestinationChoice> destinations,
      Integer failCount,
      String replayCommitRef) {}
}
