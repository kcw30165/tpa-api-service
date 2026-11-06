package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
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
 * Replay compatibility for the START outcome change: an all-unregistered success is session-backed
 * (opaque protected reference only), and an old session-less ZERO_REGISTERED snapshot is never
 * silently turned into a success - it fails closed before any protected resolution.
 */
class UsernameRecoveryStartReplayCodecAllUnregisteredTest {
  private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");

  @Test
  void anAllUnregisteredSuccessSnapshotCarriesOnlyTheOpaqueProtectedReference() {
    var port = new ReplayPort();
    var codec = codec(port);

    var captured =
        codec
            .capture(
                allUnregisteredResult(),
                new HydratedIdempotentResponse(
                    200, "application/json", "{\"first\":true}", Map.of()),
                Duration.ofMinutes(5))
            .block();

    assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(port.reference);
    assertThat(captured.snapshot().serializedSafeBody())
        .contains("SESSION_CREATED")
        .doesNotContain(
            "authFlowSessionSecret0001",
            "csrfSecret0001",
            "schemeRefSecret0001",
            "schemeRefSecret0002",
            "T001",
            "T002");
    assertThat(port.storedState).isNotNull();
    assertThat(port.storedState.authFlowSessionId())
        .isEqualTo(new AuthFlowSessionId("authFlowSessionSecret0001"));
  }

  @Test
  void aCompletedReplayOfAnAllUnregisteredSuccessResolvesItsProtectedContinuityOnce() {
    var port = new ReplayPort();
    var codec = codec(port);
    var result = allUnregisteredResult();
    port.resolveResult = result;
    var captured =
        codec
            .capture(
                result,
                new HydratedIdempotentResponse(200, "application/json", "{}", Map.of()),
                Duration.ofMinutes(5))
            .block();

    var hydrated = codec.hydrate(captured.snapshot()).block();

    assertThat(hydrated).isEqualTo(result);
    assertThat(port.resolves).isEqualTo(1);
  }

  @Test
  void anOldSessionlessZeroRegisteredSnapshotFailsClosedBeforeAnyProtectedResolution() {
    var port = new ReplayPort();
    var codec = codec(port);
    var envelope =
        new UsernameRecoveryIdempotencyResponseEnvelope(
            new JacksonTransientJsonAdapter(new ObjectMapper()));
    String legacyBody =
        envelope.encode(
            UsernameRecoveryIdempotencyAction.START,
            new UsernameRecoveryStartReplayCodec.SafePayload(
                "ZERO_REGISTERED",
                List.of(new UsernameRecoveryStartReplayCodec.SchemeWire("HK", "T001", false))));
    var legacySnapshot =
        new IdempotentTerminalResponseSnapshot(
            IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION,
            200,
            "application/json",
            legacyBody,
            Map.of(),
            null);

    assertThatThrownBy(() -> codec.hydrate(legacySnapshot).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("START replay outcome is unsupported");
    assertThat(port.resolves).isZero();
  }

  private static UsernameRecoveryStartResult allUnregisteredResult() {
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
        new AuthFlowSessionId("authFlowSessionSecret0001"),
        "csrfSecret0001",
        300,
        List.of(
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef("schemeRefSecret0001"), "HK", "T001", false),
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef("schemeRefSecret0002"), "HK", "T002", false)));
  }

  private static UsernameRecoveryStartReplayCodec codec(ReplayPort port) {
    var json = new JacksonTransientJsonAdapter(new ObjectMapper());
    return new UsernameRecoveryStartReplayCodec(
        new UsernameRecoveryIdempotencyResponseEnvelope(json),
        port,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static final class ReplayPort implements UsernameRecoveryStartProtectedReplayPort {
    private final ProtectedReplayReference reference =
        new ProtectedReplayReference("usernameRecoveryStartReplay01");
    private UsernameRecoveryStartProtectedReplayState storedState;
    private UsernameRecoveryStartResult resolveResult;
    private int resolves;

    @Override
    public Mono<ProtectedReplayReference> store(
        UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
      storedState = state;
      return Mono.just(reference);
    }

    @Override
    public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference supplied) {
      resolves++;
      return Mono.justOrEmpty(resolveResult);
    }
  }
}
