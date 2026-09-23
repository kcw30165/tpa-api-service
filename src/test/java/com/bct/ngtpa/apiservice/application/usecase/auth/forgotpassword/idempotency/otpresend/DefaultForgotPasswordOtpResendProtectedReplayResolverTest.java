package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented tests for {@link DefaultForgotPasswordOtpResendProtectedReplayResolver}. */
class DefaultForgotPasswordOtpResendProtectedReplayResolverTest {

  private final ForgotPasswordOtpReplayResolver replay =
      mock(ForgotPasswordOtpReplayResolver.class);

  private DefaultForgotPasswordOtpResendProtectedReplayResolver newResolver() {
    return new DefaultForgotPasswordOtpResendProtectedReplayResolver(replay);
  }

  private static final ProtectedReplayReference REF =
      new ProtectedReplayReference("replay-ref-1234567890abcd");

  @Test
  void constructorRejectsNullReplayResolver() {
    assertThatThrownBy(() -> new DefaultForgotPasswordOtpResendProtectedReplayResolver(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resolveRejectsNullReference() {
    assertThatThrownBy(() -> newResolver().resolve(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void activeStatusMapsToCompletedReplay() {
    when(replay.resolve(REF.value()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpReplayResult(
                    ForgotPasswordOtpReplayResult.Status.ACTIVE, "j***@example.com", 300, 4, 60)));
    StepVerifier.create(newResolver().resolve(REF))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.COMPLETED_REPLAY);
              assertThat(r.maskedDestination()).isEqualTo("j***@example.com");
            })
        .verifyComplete();
  }

  @Test
  void expiredStatusMapsToExpired() {
    when(replay.resolve(REF.value()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpReplayResult(
                    ForgotPasswordOtpReplayResult.Status.EXPIRED, "j***@example.com", 0, 4, 0)));
    StepVerifier.create(newResolver().resolve(REF))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.EXPIRED))
        .verifyComplete();
  }

  @Test
  void systemErrorStatusMapsToSystemFailed() {
    when(replay.resolve(REF.value()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpReplayResult(
                    ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR, null, null, null, null)));
    StepVerifier.create(newResolver().resolve(REF))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(r.maskedDestination()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void underlyingResolverErrorPropagates() {
    when(replay.resolve(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newResolver().resolve(REF))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
