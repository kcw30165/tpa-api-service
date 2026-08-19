package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import java.util.Objects;

/** Inbound application command. Password values must never be persisted or logged. */
public record CompleteRegistrationCommand(
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    RegistrationIdempotencyKey idempotencyKey,
    String password,
    String confirmPassword,
    String origin,
    String referer,
    String acceptLanguage,
    String correlationId,
    Duration idempotencyTtl) {

  public CompleteRegistrationCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    if (!idempotencyTtl.isPositive()) {
      throw new IllegalArgumentException("idempotencyTtl must be positive");
    }
  }

  @Override
  public String toString() {
    return "CompleteRegistrationCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, "
        + "idempotencyKey=<redacted>, password=<redacted>, confirmPassword=<redacted>, "
        + "origin=<redacted>, referer=<redacted>, acceptLanguage="
        + acceptLanguage
        + ", correlationId="
        + correlationId
        + ", idempotencyTtl="
        + idempotencyTtl
        + "]";
  }
}
