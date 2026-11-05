package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * A session-less START snapshot is valid only as MEMBER_NOT_FOUND with an empty scheme list. Any
 * other shape fails closed as an invalid payload, before any protected resolution.
 *
 * <p>The remaining defensive guard in {@code toNonSessionResult} (a decoded outcome other than
 * MEMBER_NOT_FOUND) is unreachable: SESSION_CREATED returns earlier and the enum has two values.
 */
class UsernameRecoveryStartReplayCodecMemberNotFoundPayloadTest {
  private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");

  @Test
  void aWellFormedMemberNotFoundSnapshotHydratesWithoutProtectedResolution() {
    var port = new ReplayPort();
    var snapshot =
        snapshot(new UsernameRecoveryStartReplayCodec.SafePayload("MEMBER_NOT_FOUND", List.of()));

    var result = codec(port).hydrate(snapshot).block();

    assertThat(result)
        .isEqualTo(
            UsernameRecoveryStartResult.withoutSession(
                UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, List.of()));
    assertThat(port.resolves).isZero();
  }

  @Test
  void aMemberNotFoundSnapshotWithoutASchemesListIsRejectedAsAnInvalidPayload() {
    var port = new ReplayPort();
    var snapshot =
        snapshot(new UsernameRecoveryStartReplayCodec.SafePayload("MEMBER_NOT_FOUND", null));
    var codec = codec(port);

    assertThatThrownBy(() -> codec.hydrate(snapshot).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("START replay payload is invalid");
    assertThat(port.resolves).isZero();
  }

  @Test
  void aMemberNotFoundSnapshotCarryingSchemesIsRejectedAsAnInvalidPayload() {
    var port = new ReplayPort();
    var snapshot =
        snapshot(
            new UsernameRecoveryStartReplayCodec.SafePayload(
                "MEMBER_NOT_FOUND",
                List.of(new UsernameRecoveryStartReplayCodec.SchemeWire("HK", "T001", false))));
    var codec = codec(port);

    assertThatThrownBy(() -> codec.hydrate(snapshot).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("START replay payload is invalid");
    assertThat(port.resolves).isZero();
  }

  private static IdempotentTerminalResponseSnapshot snapshot(
      UsernameRecoveryStartReplayCodec.SafePayload payload) {
    var envelope =
        new UsernameRecoveryIdempotencyResponseEnvelope(
            new JacksonTransientJsonAdapter(new ObjectMapper()));
    return new IdempotentTerminalResponseSnapshot(
        IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION,
        200,
        "application/json",
        envelope.encode(UsernameRecoveryIdempotencyAction.START, payload),
        Map.of(),
        null);
  }

  private static UsernameRecoveryStartReplayCodec codec(ReplayPort port) {
    return new UsernameRecoveryStartReplayCodec(
        new UsernameRecoveryIdempotencyResponseEnvelope(
            new JacksonTransientJsonAdapter(new ObjectMapper())),
        port,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static final class ReplayPort implements UsernameRecoveryStartProtectedReplayPort {
    private int resolves;

    @Override
    public Mono<ProtectedReplayReference> store(
        UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
      return Mono.error(
          new AssertionError("a session-less snapshot must not use protected storage"));
    }

    @Override
    public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference reference) {
      resolves++;
      return Mono.empty();
    }
  }
}
