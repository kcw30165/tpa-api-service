package com.bct.ngtpa.apiservice.application.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MessageDeliveryUnknownOutcomeSubtask005aFailFirstTest {
  private static final MessageDeliveryCommand SMS =
      new MessageDeliveryCommand(
          MessageDeliveryType.LOGIN_OTP,
          MessageDeliveryScenario.LOGIN,
          MessageDeliveryChannel.SMS,
          "91234567",
          null,
          "Code {code}",
          "login-otp",
          Map.of("code", "123456"),
          "en",
          "req-005a");

  @Test
  void approvedEnumsExposeUnknownOutcome() {
    assertThat(SenderStatus.valueOf("UNKNOWN_OUTCOME")).isEqualTo(SenderStatus.UNKNOWN_OUTCOME);
    assertThat(MessageDeliveryStatus.valueOf("UNKNOWN_OUTCOME"))
        .isEqualTo(MessageDeliveryStatus.UNKNOWN_OUTCOME);
  }

  @Test
  void acceptedAndExplicitFailureRemainBackwardCompatible() {
    assertStatus(
        new SenderResult(SenderStatus.ACCEPTED, "provider-id", null, null),
        MessageDeliveryStatus.ACCEPTED);
    assertStatus(
        new SenderResult(SenderStatus.FAILED, null, "REJECTED", "provider detail"),
        MessageDeliveryStatus.FAILED);
  }

  @Test
  void localPreInvocationFailureIsConfirmedFailed() {
    var service = adapter(message -> Mono.error(new AssertionError("sender must not run")));
    var invalid =
        new MessageDeliveryCommand(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryScenario.LOGIN,
            MessageDeliveryChannel.SMS,
            " ",
            null,
            "Code",
            null,
            Map.of(),
            "en",
            "req-005a");
    StepVerifier.create(service.deliver(invalid))
        .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED))
        .verifyComplete();
  }

  @Test
  void nullEmptyAndUnrecognizedResultsAreUnknown() {
    assertStatus(null, MessageDeliveryStatus.UNKNOWN_OUTCOME);
    assertStatus(
        new SenderResult(null, null, "SECRET", "provider detail"),
        MessageDeliveryStatus.UNKNOWN_OUTCOME);
  }

  @Test
  void timeoutAndPossiblePostTransmissionLossAreUnknownWithoutAutomaticRetry() {
    AtomicInteger timeoutCalls = new AtomicInteger();
    var timeout =
        adapter(
            message -> {
              timeoutCalls.incrementAndGet();
              return Mono.error(new TimeoutException("provider secret"));
            });
    StepVerifier.create(timeout.deliver(SMS))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(MessageDeliveryStatus.UNKNOWN_OUTCOME);
              assertThat(result.failureMessage()).doesNotContain("provider secret");
            })
        .verifyComplete();
    assertThat(timeoutCalls).hasValue(1);

    AtomicInteger resetCalls = new AtomicInteger();
    var reset =
        adapter(
            message -> {
              resetCalls.incrementAndGet();
              return Mono.error(new IllegalStateException("connection reset after write"));
            });
    StepVerifier.create(reset.deliver(SMS))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
    assertThat(resetCalls).hasValue(1);
  }

  private void assertStatus(SenderResult senderResult, MessageDeliveryStatus expected) {
    SmsSenderPort sender =
        senderResult == null ? message -> Mono.empty() : message -> Mono.just(senderResult);
    StepVerifier.create(adapter(sender).deliver(SMS))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(expected);
              assertThat(result.toString())
                  .doesNotContain("provider detail", "SECRET", "123456", "91234567");
            })
        .verifyComplete();
  }

  private MessageDeliveryAdapter adapter(SmsSenderPort sms) {
    EmailSenderPort email = message -> Mono.error(new AssertionError("email sender must not run"));
    return new MessageDeliveryAdapter(sms, email);
  }
}
