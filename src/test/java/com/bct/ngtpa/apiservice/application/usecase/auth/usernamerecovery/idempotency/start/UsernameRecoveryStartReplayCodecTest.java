package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
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

class UsernameRecoveryStartReplayCodecTest {
  private static final Instant NOW = Instant.parse("2026-09-09T02:00:00Z");

  @Test
  void sessionSnapshotIsRedactedAndUsesOnlyOpaqueProtectedReference() {
    var port = new ReplayPort();
    var codec = codec(port);
    var secretRef =
        new com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef("schemeRefSecret0001");
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            new AuthFlowSessionId("authFlowSessionSecret0001"),
            "csrfSecret0001",
            300,
            List.of(new UsernameRecoverySchemeProjection(secretRef, "HK", "T001", true)));
    var captured =
        codec
            .capture(
                result,
                new HydratedIdempotentResponse(
                    200, "application/json", "{\"first\":true}", Map.of()),
                Duration.ofMinutes(5))
            .block();
    assertThat(captured.snapshot().serializedSafeBody())
        .doesNotContain(
            "authFlowSessionSecret0001", "csrfSecret0001", "schemeRefSecret0001", "T001");
    assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(port.reference);
    assertThat(captured.response().serializedBody()).contains("first");
  }

  @Test
  void nonSessionOutcomeRoundTripsWithoutProtectedStorage() {
    var port = new ReplayPort();
    var codec = codec(port);
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
            null,
            null,
            List.of(new UsernameRecoverySchemeProjection(null, "HK", "T001", false)));
    var captured =
        codec
            .capture(
                result,
                new HydratedIdempotentResponse(
                    200, "application/json", "{\"first\":true}", Map.of()),
                Duration.ofMinutes(5))
            .block();
    assertThat(captured.snapshot().protectedReplayReference()).isNull();
    assertThat(codec.hydrate(captured.snapshot()).block()).isEqualTo(result);
    assertThat(port.resolves).isZero();
  }

  @Test
  void wrongActionFailsClosedBeforeProtectedResolution() {
    var port = new ReplayPort();
    var codec = codec(port);
    var snapshot =
        new com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"request\",\"payload\":{\"outcome\":\"SESSION_CREATED\",\"schemes\":null}}",
            Map.of(),
            port.reference);
    assertThatThrownBy(() -> codec.hydrate(snapshot).block())
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(port.resolves).isZero();
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
    private int resolves;

    @Override
    public Mono<ProtectedReplayReference> store(
        UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
      return Mono.just(reference);
    }

    @Override
    public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference reference) {
      resolves++;
      return Mono.empty();
    }
  }
}
