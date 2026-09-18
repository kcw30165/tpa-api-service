package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link UsernameRecoveryStartExecutionBridge}. Uses a REAL
 * Jackson-backed {@link TransientJsonPort} (not mocked), same technique proven in rounds 125/126.
 */
class UsernameRecoveryStartExecutionBridgeTest {

  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper = new ObjectMapper();
  private final TransientJsonPort json =
      new TransientJsonPort() {
        @Override
        public String write(Object value) {
          try {
            return mapper.writeValueAsString(value);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public <T> T read(String jsonStr, Class<T> type) {
          try {
            return mapper.readValue(jsonStr, type);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        }
      };
  private final UsernameRecoveryStartReplayCodec codec = mock(UsernameRecoveryStartReplayCodec.class);

  private UsernameRecoveryStartExecutionBridge newBridge() {
    return new UsernameRecoveryStartExecutionBridge(json, codec);
  }

  private static UsernameRecoveryStartResult memberNotFound() {
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, null, List.of());
  }

  // ----- toTransientResponse(): guard --------------------------------------------

  @Test
  void toTransientResponseRejectsNullResult() {
    assertThatThrownBy(() -> newBridge().toTransientResponse(null, 200))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- round-trip ---------------------------------------------------------------

  @Test
  void toTransientResponseThenFromTransientResponseRoundTrips() {
    UsernameRecoveryStartExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(memberNotFound(), 200);
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.mediaType()).isEqualTo(MEDIA_TYPE);
    UsernameRecoveryStartResult roundTripped = bridge.fromTransientResponse(response);
    assertThat(roundTripped.outcome())
        .isEqualTo(UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
  }

  // ----- fromTransientResponse(): guards -------------------------------------------

  @Test
  void fromTransientResponseRejectsNullResponse() {
    assertThatThrownBy(() -> newBridge().fromTransientResponse(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void fromTransientResponseWrongMediaTypeThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "text/plain", "{}", Map.of());
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseNonEmptyHeadersThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(
            200, MEDIA_TYPE, json.write(memberNotFound()), Map.of("Retry-After", "30"));
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseMalformedBodyThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, MEDIA_TYPE, "{not valid json", Map.of());
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- capture(): delegates to the codec with ttl --------------------------------

  @Test
  void captureDelegatesToCodecWithTtl() {
    UsernameRecoveryStartExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(memberNotFound(), 200);
    IdempotentCapturedResponse captured = mock(IdempotentCapturedResponse.class);
    when(codec.capture(any(), any(), any())).thenReturn(Mono.just(captured));
    StepVerifier.create(bridge.capture(response, Duration.ofMinutes(5)))
        .expectNext(captured)
        .verifyComplete();
  }

  // ----- hydrate(): delegates to the codec, propagates protectedReplayReference -------

  @Test
  void hydrateWithoutProtectedReplayReferenceReturnsPlainResponse() {
    UsernameRecoveryStartExecutionBridge bridge = newBridge();
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, MEDIA_TYPE, "{}", Map.of(), null);
    when(codec.hydrate(snapshot)).thenReturn(Mono.just(memberNotFound()));
    StepVerifier.create(bridge.hydrate(snapshot))
        .assertNext(response -> assertThat(response.protectedReplayReference()).isNull())
        .verifyComplete();
  }

  @Test
  void hydrateWithProtectedReplayReferencePropagatesIt() {
    UsernameRecoveryStartExecutionBridge bridge = newBridge();
    ProtectedReplayReference ref = new ProtectedReplayReference("replay-ref-1234567890abcd");
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, MEDIA_TYPE, "{}", Map.of(), ref);
    when(codec.hydrate(snapshot)).thenReturn(Mono.just(memberNotFound()));
    StepVerifier.create(bridge.hydrate(snapshot))
        .assertNext(response -> assertThat(response.protectedReplayReference()).isEqualTo(ref))
        .verifyComplete();
  }
}
