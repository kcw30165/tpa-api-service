package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordStartReplayCodecTest {
  private static final Instant NOW = Instant.parse("2026-09-06T13:50:00Z");
  private final ReplayPort replay = new ReplayPort();
  private final ForgotPasswordStartReplayCodec codec =
      new ForgotPasswordStartReplayCodec(
          new ForgotPasswordSharedResponseEnvelope(
              new JacksonTransientJsonAdapter(new ObjectMapper())),
          replay,
          command ->
              Mono.just(
                  new CreateAuthFlowSessionCsrfResult(
                      "fresh-csrf", NOW.plusSeconds(300), 300)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void startedSnapshotExcludesProtectedValuesAndHydratesFreshCsrf() {
    var first = started();
    var transientResponse = new HydratedIdempotentResponse(200, "application/json", "{\"transient\":true}", Map.of());
    StepVerifier.create(codec.capture(first, transientResponse, Duration.ofMinutes(5)))
        .assertNext(captured -> {
          assertThat(captured.snapshot().serializedSafeBody())
              .doesNotContain("afs-secret", "csrf-secret", "member01");
          assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(replay.reference);
          StepVerifier.create(codec.hydrate(captured.snapshot(), 1800))
              .assertNext(value -> {
                assertThat(value.status()).isEqualTo(StartForgotPasswordResultStatus.COMPLETED_REPLAY);
                assertThat(value.authFlowSessionId()).isEqualTo("afs-secret");
                assertThat(value.csrfToken()).isEqualTo("fresh-csrf");
                assertThat(value.identityType()).isEqualTo("STAFF_NO");
              })
              .verifyComplete();
        })
        .verifyComplete();
  }

  @Test
  void deterministicRejectionNeedsNoProtectedReference() {
    var result = new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
        WorkflowNextStep.ENTER_USERNAME,
        null,
        null,
        null,
        List.of(new StartForgotPasswordError("err.forgotpassword.username.notFound", "username", null)),
        null);
    var response = new HydratedIdempotentResponse(422, "application/json", "{\"transient\":true}", Map.of());
    var captured = codec.capture(result, response, Duration.ofMinutes(5)).block();
    assertThat(captured.snapshot().protectedReplayReference()).isNull();
    assertThat(codec.hydrate(captured.snapshot(), 1800).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.USERNAME_NOT_FOUND);
    assertThat(replay.loads).isZero();
  }

  @Test
  void failsClosedForExpiredProtectedReplayAndLifecycleStatus() {
    replay.state = new ForgotPasswordStartProtectedReplayState("afs-secret", "STAFF_NO", NOW.minusSeconds(1));
    var captured = codec.capture(started(), new HydratedIdempotentResponse(200, "application/json", "{\"transient\":true}", Map.of()), Duration.ofMinutes(5)).block();
    assertThatThrownBy(() -> codec.hydrate(captured.snapshot(), 1800).block())
        .hasMessageContaining("expired");
    var lifecycle = new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.UNKNOWN_OUTCOME,
        WorkflowNextStep.NONE,
        null,
        null,
        null,
        List.of(),
        null);
    assertThatThrownBy(() -> codec.capture(lifecycle, new HydratedIdempotentResponse(500, "application/json", "{}", Map.of()), Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private StartForgotPasswordResult started() {
    return new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.STARTED,
        WorkflowNextStep.ENTER_ID,
        "csrf-secret",
        "STAFF_NO",
        "afs-secret",
        List.of(),
        null);
  }

  private static final class ReplayPort implements ForgotPasswordStartProtectedReplayPort {
    private final ProtectedReplayReference reference = new ProtectedReplayReference("forgotPasswordStartReplay01");
    private ForgotPasswordStartProtectedReplayState state;
    private int loads;

    @Override
    public Mono<ProtectedReplayReference> store(ForgotPasswordStartProtectedReplayState state, Duration ttl) {
      this.state = state;
      return Mono.just(reference);
    }

    @Override
    public Mono<ForgotPasswordStartProtectedReplayState> load(ProtectedReplayReference reference) {
      loads++;
      return Mono.just(state);
    }
  }
}
