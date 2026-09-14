package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ForgotPasswordStartExecutionBridgeTest {
  private static final Instant NOW = Instant.parse("2026-09-06T13:50:00Z");

  @Test
  void transientProtectedResultRoundTripsAndSnapshotRemainsSafe() {
    var port = new ReplayPort();
    var json = new JacksonTransientJsonAdapter(new ObjectMapper());
    var codec =
        new ForgotPasswordStartReplayCodec(
            new ActionTaggedIdempotencyResponseEnvelope(json),
            port,
            command ->
                Mono.just(
                    new CreateAuthFlowSessionCsrfResult("fresh-csrf", NOW.plusSeconds(300), 300)),
            Clock.fixed(NOW, ZoneOffset.UTC));
    var bridge = new ForgotPasswordStartExecutionBridge(json, codec, 1800);
    var result =
        new StartForgotPasswordResult(
            StartForgotPasswordResultStatus.STARTED,
            WorkflowNextStep.ENTER_ID,
            "csrf-secret",
            "STAFF_NO",
            "afs-secret",
            List.of(),
            null);
    var transientResponse = bridge.toTransientResponse(result);
    assertThat(bridge.fromTransientResponse(transientResponse))
        .usingRecursiveComparison()
        .isEqualTo(result);
    var captured = bridge.capture(transientResponse, Duration.ofMinutes(5)).block();
    assertThat(captured.snapshot().serializedSafeBody())
        .doesNotContain("csrf-secret", "afs-secret");
    var replayed = bridge.fromTransientResponse(bridge.hydrate(captured.snapshot()).block());
    assertThat(replayed.status()).isEqualTo(StartForgotPasswordResultStatus.COMPLETED_REPLAY);
    assertThat(replayed.csrfToken()).isEqualTo("fresh-csrf");
  }

  @Test
  void malformedTransientBodyFailsClosed() {
    var json = new JacksonTransientJsonAdapter(new ObjectMapper());
    var port = new ReplayPort();
    var codec =
        new ForgotPasswordStartReplayCodec(
            new ActionTaggedIdempotencyResponseEnvelope(json),
            port,
            command -> Mono.error(new AssertionError("must not create CSRF")),
            Clock.systemUTC());
    var bridge = new ForgotPasswordStartExecutionBridge(json, codec, 1800);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse(
                        200, "application/json", "{}", java.util.Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class ReplayPort implements ForgotPasswordStartProtectedReplayPort {
    private ForgotPasswordStartProtectedReplayState state;

    @Override
    public Mono<ProtectedReplayReference> store(
        ForgotPasswordStartProtectedReplayState state, Duration ttl) {
      this.state = state;
      return Mono.just(new ProtectedReplayReference("forgotPasswordStartReplay01"));
    }

    @Override
    public Mono<ForgotPasswordStartProtectedReplayState> load(ProtectedReplayReference reference) {
      return Mono.just(state);
    }
  }
}
