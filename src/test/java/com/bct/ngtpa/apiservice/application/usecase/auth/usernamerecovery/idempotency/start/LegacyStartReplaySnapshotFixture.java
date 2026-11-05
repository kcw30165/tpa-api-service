package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Builds persisted START replay snapshots for tests that live outside this package. It uses the real
 * wire records and the real response envelope, so the shapes are the ones the codec itself writes
 * and reads.
 */
public final class LegacyStartReplaySnapshotFixture {
  private LegacyStartReplaySnapshotFixture() {}

  /**
   * The session-less snapshot that was persisted while the ZERO_REGISTERED outcome still existed:
   * outcome name plus the unregistered scheme rows, no protected replay reference, HTTP status 200
   * (the service stores every outcome with the same transient status).
   */
  public static IdempotentTerminalResponseSnapshot zeroRegistered(
      UsernameRecoveryIdempotencyResponseEnvelope envelope) {
    String body =
        envelope.encode(
            UsernameRecoveryIdempotencyAction.START,
            new UsernameRecoveryStartReplayCodec.SafePayload(
                "ZERO_REGISTERED",
                List.of(new UsernameRecoveryStartReplayCodec.SchemeWire("HK", "T001", false))));
    return new IdempotentTerminalResponseSnapshot(
        IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION,
        200,
        "application/json",
        body,
        Map.of(),
        null);
  }

  /** A well-formed, completed MEMBER_NOT_FOUND snapshot captured by the real codec. */
  public static IdempotentTerminalResponseSnapshot memberNotFound(
      UsernameRecoveryStartReplayCodec codec) {
    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, List.of());
    return codec
        .capture(
            result,
            new HydratedIdempotentResponse(200, "application/json", "{\"first\":true}", Map.of()),
            Duration.ofMinutes(5))
        .block()
        .snapshot();
  }
}
