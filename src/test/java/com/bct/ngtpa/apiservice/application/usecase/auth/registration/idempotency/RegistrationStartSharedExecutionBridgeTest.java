package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RegistrationStartSharedExecutionBridgeTest {
  @Test
  void transientResponseRoundTripsProtectedStartedResultInMemory() {
    var bridge = bridge(new FakeProtectedReplayPort());
    var first = started();
    var transientResponse = bridge.toTransientResponse(first);
    assertThat(transientResponse.serializedBody()).contains("csrf-secret", "session-secret");
    assertThat(bridge.fromTransientResponse(transientResponse))
        .usingRecursiveComparison()
        .isEqualTo(first);
  }

  @Test
  void captureRemovesProtectedValuesBeforeSharedSnapshotPersistence() {
    var protectedPort = new FakeProtectedReplayPort();
    var bridge = bridge(protectedPort);
    var captured =
        bridge.capture(bridge.toTransientResponse(started()), Duration.ofMinutes(5)).block();
    assertThat(captured.snapshot().serializedSafeBody())
        .doesNotContain("csrf-secret", "session-secret");
    assertThat(captured.snapshot().protectedReplayReference()).isNotNull();
    assertThat(protectedPort.storeCalls).isEqualTo(1);
  }

  @Test
  void completedReplayHydratesProtectedStateThenRestoresTypedStartedResult() {
    var protectedPort = new FakeProtectedReplayPort();
    var bridge = bridge(protectedPort);
    var captured =
        bridge.capture(bridge.toTransientResponse(started()), Duration.ofMinutes(5)).block();
    var replayResponse = bridge.hydrate(captured.snapshot()).block();
    var replay = bridge.fromTransientResponse(replayResponse);
    assertThat(replay.status()).isEqualTo(StartRegistrationResultStatus.STARTED);
    assertThat(replay.csrfToken()).isEqualTo("csrf-secret");
    assertThat(replay.authFlowSessionId()).isEqualTo("session-secret");
    assertThat(protectedPort.loadCalls).isEqualTo(1);
  }

  @Test
  void malformedTransientResponseFailsClosed() {
    var bridge = bridge(new FakeProtectedReplayPort());
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse(
                        200, "application/json", "{\"unknown\":true}", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RegistrationStartSharedExecutionBridge bridge(
      RegistrationStartProtectedReplayPort protectedPort) {
    return new RegistrationStartSharedExecutionBridge(
        new RegistrationStartReplayCodec(), protectedPort);
  }

  private static StartRegistrationResult started() {
    return new StartRegistrationResult(
        StartRegistrationResultStatus.STARTED,
        WorkflowNextStep.VERIFY_OTP,
        "m****r@example.com",
        300,
        60,
        3,
        "csrf-secret",
        "session-secret",
        List.of(),
        List.of(),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private static final class FakeProtectedReplayPort
      implements RegistrationStartProtectedReplayPort {
    private final ProtectedReplayReference reference =
        new ProtectedReplayReference("startExecutionReplayReference01");
    private RegistrationStartProtectedReplayState state;
    private int storeCalls;
    private int loadCalls;

    @Override
    public Mono<ProtectedReplayReference> store(
        RegistrationStartProtectedReplayState state, Duration ttl) {
      storeCalls++;
      this.state = state;
      return Mono.just(reference);
    }

    @Override
    public Mono<RegistrationStartProtectedReplayState> load(ProtectedReplayReference reference) {
      loadCalls++;
      return Mono.just(state);
    }
  }
}
