package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link ForgotPasswordOtpResendExecutionBridge}. Uses a REAL
 * TransientJsonPort backed by Jackson (not mocked) to exercise the real Wire record round-trip.
 */
class ForgotPasswordOtpResendExecutionBridgeTest {

  private final com.fasterxml.jackson.databind.ObjectMapper mapper =
      new com.fasterxml.jackson.databind.ObjectMapper();
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
  private final ForgotPasswordOtpResendReplayCodec codec =
      mock(ForgotPasswordOtpResendReplayCodec.class);

  private ForgotPasswordOtpResendExecutionBridge newBridge() {
    return new ForgotPasswordOtpResendExecutionBridge(json, codec);
  }

  private static ResendForgotPasswordOtpResult resent() {
    return new ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
  }

  @Test
  void toTransientResponseThenFromTransientResponseRoundTrips() {
    ForgotPasswordOtpResendExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(resent());
    assertThat(response.mediaType()).isEqualTo("application/json");
    ResendForgotPasswordOtpResult roundTripped = bridge.fromTransientResponse(response);
    assertThat(roundTripped.status()).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
    assertThat(roundTripped.maskedDestination()).isEqualTo("j***@example.com");
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
        new HydratedIdempotentResponse(200, "application/json", json.write(resent()), Map.of("Retry-After", "30"));
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseHttpStatusMismatchThrows() {
    ForgotPasswordOtpResendExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse valid = bridge.toTransientResponse(resent());
    HydratedIdempotentResponse mismatched =
        new HydratedIdempotentResponse(404, valid.mediaType(), valid.serializedBody(), Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(mismatched))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void captureDelegatesToCodec() {
    ForgotPasswordOtpResendExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(resent());
    ProtectedReplayReference ref = new ProtectedReplayReference("replay-ref-1234567890abcd");
    IdempotentCapturedResponse captured = mock(IdempotentCapturedResponse.class);
    when(codec.capture(any(), any(), any())).thenReturn(Mono.just(captured));
    StepVerifier.create(bridge.capture(response, ref)).expectNext(captured).verifyComplete();
  }

  @Test
  void hydrateDelegatesToCodecAndMapsResult() {
    ForgotPasswordOtpResendExecutionBridge bridge = newBridge();
    IdempotentTerminalResponseSnapshot snapshot = mock(IdempotentTerminalResponseSnapshot.class);
    when(codec.hydrate(snapshot)).thenReturn(Mono.just(resent()));
    StepVerifier.create(bridge.hydrate(snapshot))
        .assertNext(response -> assertThat(response.mediaType()).isEqualTo("application/json"))
        .verifyComplete();
  }
}
