package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendRejectedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpSendRateLimitedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** OTP Resend business workflow after authorized context loading and idempotency acquisition. */
public final class RegistrationOtpResendProtectedOperation {
  private static final String SOURCE_SERVER = "SERVER";
  private final RegistrationOtpPort otp;
  private final RegistrationSessionPort sessions;
  private final RegistrationMessagePort messages;

  public RegistrationOtpResendProtectedOperation(
      RegistrationOtpPort otp, RegistrationSessionPort sessions, RegistrationMessagePort messages) {
    this.otp = Objects.requireNonNull(otp);
    this.sessions = Objects.requireNonNull(sessions);
    this.messages = Objects.requireNonNull(messages);
  }

  public Mono<ResendRegistrationOtpResult> execute(
      ResendRegistrationOtpCommand command,
      RegistrationProtectedAuthFlowContextResult protectedContext) {
    Objects.requireNonNull(command);
    Objects.requireNonNull(protectedContext);
    RegistrationAuthFlowContext context = protectedContext.registrationContext();
    RegistrationContactType contactType = context.contactType();
    String contactValue = context.contactValue();
    return otp.resendOtp(protectedContext.activeOtpChallengeId(), contactType, contactValue)
        .flatMap(
            created ->
                sessions
                    .updateOtpChallenge(
                        command.authFlowSessionId().value(),
                        challengeId(created),
                        contactType,
                        protectedContext.maskedDestination())
                    .then(
                        messages.deliverRegistrationOtp(
                            contactType,
                            contactValue,
                            rawCode(created),
                            created.expiresInSeconds(),
                            command.acceptLanguage(),
                            command.correlationId()))
                    .map(
                        accepted ->
                            Boolean.TRUE.equals(accepted)
                                ? resent(protectedContext, created)
                                : failure(
                                    ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED,
                                    WorkflowNextStep.RESEND_OTP,
                                    RegistrationOtpResendErrorKeys
                                        .REGISTRATION_MESSAGE_DELIVERY_FAILED,
                                    null,
                                    null)))
        .onErrorResume(
            RegistrationOtpSendRateLimitedException.class,
            error ->
                Mono.just(
                    failure(
                        ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED,
                        WorkflowNextStep.RESTART_FLOW,
                        RegistrationOtpResendErrorKeys.REGISTRATION_OTP_SEND_RATE_LIMITED,
                        error.retryAfterSeconds(),
                        null)))
        .onErrorResume(
            RegistrationOtpResendRejectedException.class,
            error ->
                Mono.just(
                    failure(
                        error.status(),
                        error.status()
                                == ResendRegistrationOtpResultStatus.MAX_RESEND_ATTEMPTS_REACHED
                            ? WorkflowNextStep.RESTART_FLOW
                            : WorkflowNextStep.VERIFY_OTP,
                        errorCode(error.status()),
                        error.retryAfterSeconds(),
                        error.remainingResendAttempts())));
  }

  private ResendRegistrationOtpResult resent(
      RegistrationProtectedAuthFlowContextResult context, RegistrationOtpCreationResult created) {
    return new ResendRegistrationOtpResult(
        ResendRegistrationOtpResultStatus.RESENT,
        WorkflowNextStep.VERIFY_OTP,
        context.maskedDestination(),
        created.expiresInSeconds(),
        created.resendCooldownSeconds(),
        created.remainingResendAttempts(),
        null /* retryAfterSeconds */,
        List.of(),
        List.of(),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private ResendRegistrationOtpResult failure(
      ResendRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      String code,
      Integer retryAfterSeconds,
      Integer remainingAttempts) {
    return new ResendRegistrationOtpResult(
        status,
        nextStep,
        null,
        0,
        0,
        remainingAttempts == null ? 0 : remainingAttempts,
        retryAfterSeconds,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.FORM,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private String errorCode(ResendRegistrationOtpResultStatus status) {
    return switch (status) {
      case COOLDOWN_ACTIVE -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_COOLDOWN_ACTIVE;
      case SEND_RATE_LIMITED -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_SEND_RATE_LIMITED;
      case MAX_RESEND_ATTEMPTS_REACHED ->
          RegistrationOtpResendErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
      case INVALID_AUTH_FLOW -> RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID;
      default -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED;
    };
  }

  private String challengeId(RegistrationOtpCreationResult result) {
    Object value =
        first(
            result,
            "otpChallengeId",
            "activeOtpChallengeId",
            "newOtpChallengeId",
            "challengeId",
            "id");
    if (value == null)
      throw new IllegalStateException("OTP resend result does not contain a challenge id");
    return String.valueOf(value);
  }

  private String rawCode(RegistrationOtpCreationResult result) {
    Object value = first(result, "rawCode", "rawOtpCode", "otpCode", "code");
    if (value == null)
      throw new IllegalStateException("OTP resend result does not contain a raw code for delivery");
    return String.valueOf(value);
  }

  private Object first(RegistrationOtpCreationResult target, String... names) {
    if (target == null) return null;
    for (String name : names) {
      try {
        Object value = target.getClass().getMethod(name).invoke(target);
        if (value != null) return value;
      } catch (ReflectiveOperationException ignored) {
        // Accessor compatibility only. Never log protected OTP values.
      }
    }
    return null;
  }
}
