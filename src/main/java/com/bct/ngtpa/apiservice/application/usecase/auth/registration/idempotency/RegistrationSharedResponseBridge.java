package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Map;
import java.util.Objects;

/**
 * Captures and hydrates deterministic Registration responses without performing action side
 * effects.
 */
public final class RegistrationSharedResponseBridge {
  public static final int SCHEMA_VERSION = 1;
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();

  public IdempotentCapturedResponse capture(RegistrationSharedTerminalResponse terminal) {
    Objects.requireNonNull(terminal, "registration terminal response must not be null");
    HydratedIdempotentResponse hydrated =
        new HydratedIdempotentResponse(
            terminal.httpStatus(),
            terminal.mediaType(),
            terminal.serializedSafeBody(),
            terminal.safeHeaders());
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION,
            terminal.httpStatus(),
            terminal.mediaType(),
            terminal.serializedSafeBody(),
            terminal.safeHeaders(),
            terminal.protectedReplayReference() == null
                ? null
                : new ProtectedReplayReference(terminal.protectedReplayReference()));
    return new IdempotentCapturedResponse(hydrated, snapshot);
  }

  public HydratedIdempotentResponse hydrate(
      RegistrationSharedIdempotencyAction expectedAction,
      IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(expectedAction, "expected registration action must not be null");
    Objects.requireNonNull(snapshot, "shared terminal snapshot must not be null");
    if (snapshot.schemaVersion() != SCHEMA_VERSION) {
      throw new IllegalArgumentException("unsupported Registration replay schema");
    }
    validateActionEnvelope(expectedAction, snapshot.serializedSafeBody());
    return new HydratedIdempotentResponse(
        snapshot.httpStatus(),
        snapshot.mediaType(),
        snapshot.serializedSafeBody(),
        snapshot.safeHeaders() == null ? Map.of() : snapshot.safeHeaders());
  }

  private void validateActionEnvelope(
      RegistrationSharedIdempotencyAction expectedAction, String body) {
    if (envelope.decodeAction(body) != expectedAction) {
      throw new IllegalArgumentException("Registration replay action mismatch");
    }
  }
}
