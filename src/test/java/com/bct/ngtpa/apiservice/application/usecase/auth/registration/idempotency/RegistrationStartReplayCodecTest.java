package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RegistrationStartReplayCodecTest {
  private final RegistrationStartReplayCodec codec = new RegistrationStartReplayCodec();

  @Test
  void startedSnapshotExcludesProtectedValuesAndHydratesOriginalPublicStatus() {
    var protectedPort = new FakeProtectedReplayPort();
    StartRegistrationResult first = started();
    var captured = codec.capture(first, protectedPort, Duration.ofMinutes(5)).block();
    assertThat(captured.snapshot().serializedSafeBody())
        .doesNotContain("csrf-secret", "auth-flow-session-secret");
    assertThat(captured.snapshot().protectedReplayReference()).isNotNull();
    StartRegistrationResult replay = codec.hydrate(captured.snapshot(), protectedPort).block();
    assertThat(replay.status()).isEqualTo(StartRegistrationResultStatus.STARTED);
    assertThat(replay.nextStep()).isEqualTo(first.nextStep());
    assertThat(replay.maskedDestination()).isEqualTo(first.maskedDestination());
    assertThat(replay.csrfToken()).isEqualTo("csrf-secret");
    assertThat(replay.authFlowSessionId()).isEqualTo("auth-flow-session-secret");
  }

  @Test
  void deterministicFailureNeedsNoProtectedReferenceAndReplaysSameError() {
    var protectedPort = new FakeProtectedReplayPort();
    StartRegistrationResult first =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VERIFICATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(
                    RegistrationErrorKey.START_VERIFICATION_FAILED, "identityValue")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var captured = codec.capture(first, protectedPort, Duration.ofMinutes(5)).block();
    assertThat(captured.snapshot().protectedReplayReference()).isNull();
    StartRegistrationResult replay = codec.hydrate(captured.snapshot(), protectedPort).block();
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.errors()).usingRecursiveComparison().isEqualTo(first.errors());
    assertThat(protectedPort.loadCalls).isZero();
  }

  @Test
  void missingProtectedReplayFailsClosed() {
    var stored = new FakeProtectedReplayPort();
    var captured = codec.capture(started(), stored, Duration.ofMinutes(5)).block();
    stored.available = false;
    assertThatThrownBy(() -> codec.hydrate(captured.snapshot(), stored).block())
        .hasMessageContaining("protected replay is unavailable");
  }

  @Test
  void protectedStateDiagnosticRenderingRedactsBothValues() {
    var state =
        new RegistrationStartProtectedReplayState("csrf-secret", "auth-flow-session-secret");
    assertThat(state.toString()).doesNotContain("csrf-secret", "auth-flow-session-secret");
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
        "auth-flow-session-secret",
        List.of(),
        List.of(),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private static final class FakeProtectedReplayPort
      implements RegistrationStartProtectedReplayPort {
    private final ProtectedReplayReference reference =
        new ProtectedReplayReference("startReplayReference0001");
    private RegistrationStartProtectedReplayState state;
    private boolean available = true;
    private int loadCalls;

    @Override
    public Mono<ProtectedReplayReference> store(
        RegistrationStartProtectedReplayState state, Duration ttl) {
      this.state = state;
      return Mono.just(reference);
    }

    @Override
    public Mono<RegistrationStartProtectedReplayState> load(ProtectedReplayReference reference) {
      loadCalls++;
      return available ? Mono.just(state) : Mono.empty();
    }
  }
}
