package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link UsernameRecoveryCancelReplayCodec}. Uses a REAL {@link
 * UsernameRecoveryIdempotencyResponseEnvelope} backed by a real Jackson-based {@link
 * TransientJsonPort} implementation (same technique proven in round 125's bridge tests).
 */
class UsernameRecoveryCancelReplayCodecTest {

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
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope =
      new UsernameRecoveryIdempotencyResponseEnvelope(json);

  private UsernameRecoveryCancelReplayCodec newCodec() {
    return new UsernameRecoveryCancelReplayCodec(envelope);
  }

  private static UsernameRecoveryCancelResult success() {
    return new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);
  }

  private static HydratedIdempotentResponse response(String mediaType, Map<String, String> headers) {
    return new HydratedIdempotentResponse(200, mediaType, "body", headers);
  }

  // ----- constructor guard --------------------------------------------------

  @Test
  void constructorRejectsNullEnvelope() {
    assertThatThrownBy(() -> new UsernameRecoveryCancelReplayCodec(null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- capture(): guards --------------------------------------------------

  @Test
  void captureNullResultThrows() {
    assertThatThrownBy(() -> newCodec().capture(null, response(MEDIA_TYPE, Map.of())))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void captureWrongMediaTypeThrows() {
    assertThatThrownBy(() -> newCodec().capture(success(), response("text/plain", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void captureNonEmptyHeadersThrows() {
    assertThatThrownBy(
            () -> newCodec().capture(success(), response(MEDIA_TYPE, Map.of("Retry-After", "30"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- capture()/hydrate() round-trip ---------------------------------------------

  @Test
  void captureThenHydrateRoundTrips() {
    UsernameRecoveryCancelReplayCodec codec = newCodec();
    IdempotentCapturedResponse captured =
        codec.capture(success(), response(MEDIA_TYPE, Map.of())).block();
    assertThat(captured).isNotNull();
    UsernameRecoveryCancelResult rehydrated = codec.hydrate(captured.snapshot()).block();
    assertThat(rehydrated.outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS);
  }

  // ----- hydrate(): guards --------------------------------------------------

  @Test
  void hydrateNullSnapshotThrows() {
    assertThatThrownBy(() -> newCodec().hydrate(null)).isInstanceOf(NullPointerException.class);
  }

  // NOTE (round 133): there is no dedicated "wrong media type" hydrate() test.
  // IdempotentTerminalResponseSnapshot's OWN compact constructor (confirmed
  // round 132) already rejects any non-"application/json" mediaType at
  // construction time - so the codec's redundant equalsIgnoreCase(MEDIA_TYPE)
  // check inside hydrate() is unreachable via any real snapshot instance
  // (same category as prior dead-code findings elsewhere in this initiative).

  @Test
  void hydrateNonEmptyHeadersErrors() {
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", "{}", Map.of("Retry-After", "30"), null);
    StepVerifier.create(newCodec().hydrate(snapshot))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateMalformedPayloadErrors() {
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, "application/json", "{not valid", Map.of(), null);
    StepVerifier.create(newCodec().hydrate(snapshot))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateUnknownOutcomeErrors() {
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", "{\"outcome\":\"NOT_A_REAL_OUTCOME\"}", Map.of(), null);
    StepVerifier.create(newCodec().hydrate(snapshot))
        .expectError(IllegalArgumentException.class)
        .verify();
  }
}
