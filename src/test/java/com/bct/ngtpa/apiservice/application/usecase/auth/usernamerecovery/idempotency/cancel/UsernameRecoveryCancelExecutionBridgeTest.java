package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link UsernameRecoveryCancelExecutionBridge}. Uses a REAL Jackson-
 * backed {@link TransientJsonPort} (not mocked), same technique proven in rounds 125/126.
 */
class UsernameRecoveryCancelExecutionBridgeTest {

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
  private final UsernameRecoveryCancelReplayCodec codec =
      mock(UsernameRecoveryCancelReplayCodec.class);

  private UsernameRecoveryCancelExecutionBridge newBridge() {
    return new UsernameRecoveryCancelExecutionBridge(json, codec);
  }

  private static UsernameRecoveryCancelResult success() {
    return new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);
  }

  // ----- constructor guards --------------------------------------------------

  @Test
  void constructorRejectsNullJson() {
    assertThatThrownBy(() -> new UsernameRecoveryCancelExecutionBridge(null, codec))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullCodec() {
    assertThatThrownBy(() -> new UsernameRecoveryCancelExecutionBridge(json, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- toTransientResponse() / fromTransientResponse() round-trip ------------------

  @Test
  void toTransientResponseThenFromTransientResponseRoundTrips() {
    UsernameRecoveryCancelExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(success(), 200);
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.mediaType()).isEqualTo(MEDIA_TYPE);
    UsernameRecoveryCancelResult roundTripped = bridge.fromTransientResponse(response);
    assertThat(roundTripped.outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS);
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
            200, MEDIA_TYPE, json.write(success()), Map.of("Retry-After", "30"));
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseCarryingProtectedReplayReferenceThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, MEDIA_TYPE, json.write(success()), Map.of())
            .withProtectedReplayReference(
                new com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference(
                    "replay-ref-1234567890abcd"));
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

  // ----- capture() / hydrate() delegate to the codec --------------------------------

  @Test
  void captureDelegatesToCodec() {
    UsernameRecoveryCancelExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(success(), 200);
    IdempotentCapturedResponse captured = mock(IdempotentCapturedResponse.class);
    when(codec.capture(any(), any())).thenReturn(Mono.just(captured));
    StepVerifier.create(bridge.capture(response)).expectNext(captured).verifyComplete();
  }

  @Test
  void hydrateDelegatesToCodecAndMapsResult() {
    UsernameRecoveryCancelExecutionBridge bridge = newBridge();
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, MEDIA_TYPE, "{}", Map.of(), null);
    when(codec.hydrate(snapshot)).thenReturn(Mono.just(success()));
    StepVerifier.create(bridge.hydrate(snapshot))
        .assertNext(response -> assertThat(response.httpStatus()).isEqualTo(200))
        .verifyComplete();
  }
}
