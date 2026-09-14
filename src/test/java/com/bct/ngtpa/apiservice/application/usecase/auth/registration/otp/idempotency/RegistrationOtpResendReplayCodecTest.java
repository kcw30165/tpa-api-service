package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendReplayCodecTest {
  private final RegistrationOtpResendReplayCodec codec = new RegistrationOtpResendReplayCodec();

  @Test
  void capturesAndHydratesAllApprovedPublicFields() {
    var first =
        new ResendRegistrationOtpResult(
            ResendRegistrationOtpResultStatus.COOLDOWN_ACTIVE,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            287,
            47,
            3,
            47,
            List.of(
                new WorkflowMessageDescriptor(
                    WorkflowMessageType.INFO, "msg.code", "message", "PAGE")),
            List.of(
                new WorkflowErrorDescriptor(
                    WorkflowErrorType.BUSINESS,
                    "err.code",
                    "error",
                    List.of("PAGE"),
                    WorkflowErrorSeverity.WARNING,
                    "SERVER")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var captured = codec.capture(first);
    var replay = codec.hydrate(captured.snapshot());
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.nextStep()).isEqualTo(first.nextStep());
    assertThat(replay.maskedDestination()).isEqualTo(first.maskedDestination());
    assertThat(replay.expiresInSeconds()).isEqualTo(first.expiresInSeconds());
    assertThat(replay.resendCooldownSeconds()).isEqualTo(first.resendCooldownSeconds());
    assertThat(replay.remainingResendAttempts()).isEqualTo(first.remainingResendAttempts());
    assertThat(replay.retryAfterSeconds()).isEqualTo(first.retryAfterSeconds());
    assertThat(replay.messages()).isEqualTo(first.messages());
    assertThat(replay.errors()).isEqualTo(first.errors());
    assertThat(replay.idempotencyOutcome())
        .isEqualTo(RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY);
    assertThat(captured.snapshot().protectedReplayReference()).isNull();
  }

  @Test
  void snapshotExcludesProtectedAndTransportValues() {
    String body =
        codec
            .capture(
                new ResendRegistrationOtpResult(
                    ResendRegistrationOtpResultStatus.RESENT,
                    WorkflowNextStep.VERIFY_OTP,
                    "m****r@example.com",
                    300,
                    60,
                    4,
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null))
            .snapshot()
            .serializedSafeBody();
    assertThat(body).contains("otp-resend", "m****r@example.com", "VERIFY_OTP");
    assertThat(body)
        .doesNotContain(
            "member@example.com",
            "123456",
            "otp-new",
            "otp-old",
            "afs-secret",
            "csrf-secret",
            "correlation",
            "Accept-Language");
  }

  @Test
  void malformedOrWrongActionReplayFailsClosed() {
    var malformed =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"otp-resend\",\"payload\":{}}",
            Map.of(),
            null);
    assertThatThrownBy(() -> codec.hydrate(malformed)).isInstanceOf(IllegalArgumentException.class);
    var wrong =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"start\",\"payload\":{}}",
            Map.of(),
            null);
    assertThatThrownBy(() -> codec.hydrate(wrong)).isInstanceOf(IllegalArgumentException.class);
  }
}
