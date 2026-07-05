package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class ResendRegistrationOtpService implements ResendRegistrationOtpUseCase {
    private static final String SOURCE_SERVER = "SERVER";

    private final RegistrationProtectedAuthFlowContextService protectedContextService;
    private final RegistrationRequestHashGenerator requestHashGenerator;
    private final RegistrationIdempotencyPort idempotencyPort;
    private final RegistrationOtpGateway otpGateway;
    private final RegistrationSessionGateway sessionGateway;
    private final RegistrationMessageGateway messageGateway;
    private final Clock clock;

    public ResendRegistrationOtpService(
            RegistrationProtectedAuthFlowContextService protectedContextService,
            RegistrationRequestHashGenerator requestHashGenerator,
            RegistrationIdempotencyPort idempotencyPort,
            RegistrationOtpGateway otpGateway,
            RegistrationSessionGateway sessionGateway,
            RegistrationMessageGateway messageGateway,
            Clock clock) {
        this.protectedContextService = Objects.requireNonNull(protectedContextService);
        this.requestHashGenerator = Objects.requireNonNull(requestHashGenerator);
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort);
        this.otpGateway = Objects.requireNonNull(otpGateway);
        this.sessionGateway = Objects.requireNonNull(sessionGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<ResendRegistrationOtpResult> resend(ResendRegistrationOtpCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return protectedContextService.load(new RegistrationProtectedAuthFlowContextCommand(
                        command.authFlowSessionId(),
                        command.csrfToken(),
                        command.origin(),
                        command.referer(),
                        command.correlationId()))
                .flatMap(context -> {
                    if (context.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID) {
                        return Mono.just(failure(
                                ResendRegistrationOtpResultStatus.CSRF_INVALID,
                                WorkflowNextStep.NONE,
                                RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID,
                                RegistrationIdempotencyStartOutcome.STARTED));
                    }
                    if (!context.isValid()) {
                        return Mono.just(failure(
                                ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
                                WorkflowNextStep.NONE,
                                RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID,
                                RegistrationIdempotencyStartOutcome.STARTED));
                    }
                    return processIdempotent(command, context);
                });
    }

    private Mono<ResendRegistrationOtpResult> processIdempotent(
            ResendRegistrationOtpCommand command,
            RegistrationProtectedAuthFlowContextResult context) {
        var hash = requestHashGenerator.hash(
                RegistrationIdempotencyAction.OTP_RESEND,
                List.of(command.authFlowSessionId().value()));
        var startCommand = new RegistrationIdempotencyStartCommand(
                RegistrationIdempotencyAction.OTP_RESEND,
                command.idempotencyKey(),
                hash,
                clock.instant(),
                command.idempotencyTtl());
        return idempotencyPort.startProcessing(startCommand)
                .flatMap(start -> switch (start.outcome()) {
                    case STARTED -> performResend(command, context, hash);
                    case DUPLICATE_IN_PROGRESS -> Mono.just(waitResult(start.outcome()));
                    case COMPLETED_REPLAY -> Mono.just(completedReplay(start));
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(failure(
                            ResendRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED,
                            WorkflowNextStep.NONE,
                            RegistrationOtpResendErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED,
                            start.outcome()));
                    case FAILED_EXISTING -> Mono.just(failure(
                            ResendRegistrationOtpResultStatus.SYSTEM_FAILED,
                            WorkflowNextStep.NONE,
                            RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED,
                            start.outcome()));
                });
    }

    private Mono<ResendRegistrationOtpResult> performResend(
            ResendRegistrationOtpCommand command,
            RegistrationProtectedAuthFlowContextResult protectedContext,
            RegistrationRequestHash hash) {
        RegistrationAuthFlowContext context = protectedContext.registrationContext();
        RegistrationContactType contactType = context.contactType();
        String contactValue = context.contactValue();
        return otpGateway.resendOtp(protectedContext.activeOtpChallengeId(), contactType, contactValue)
                .flatMap(otp -> sessionGateway.updateOtpChallenge(
                                command.authFlowSessionId().value(),
                                otpChallengeId(otp),
                                contactType,
                                protectedContext.maskedDestination())
                        .then(messageGateway.deliverRegistrationOtp(
                                contactType,
                                contactValue,
                                rawOtpCode(otp),
                                otp.expiresInSeconds(),
                                command.acceptLanguage(),
                                command.correlationId()))
                        .flatMap(accepted -> {
                            if (!Boolean.TRUE.equals(accepted)) {
                                return markFailed(command, hash).then(Mono.just(failure(
                                        ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED,
                                        WorkflowNextStep.VERIFY_OTP,
                                        RegistrationOtpResendErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED,
                                        RegistrationIdempotencyStartOutcome.STARTED)));
                            }
                            var result = new ResendRegistrationOtpResult(
                                    ResendRegistrationOtpResultStatus.RESENT,
                                    WorkflowNextStep.VERIFY_OTP,
                                    protectedContext.maskedDestination(),
                                    otp.expiresInSeconds(),
                                    otp.resendCooldownSeconds(),
                                    otp.remainingResendAttempts(),
                                    List.of(),
                                    List.of(),
                                    RegistrationIdempotencyStartOutcome.STARTED,
                                    null);
                            return markCompleted(command, hash, result).then(Mono.just(result));
                        }))
                .onErrorResume(error -> markFailed(command, hash)
                        .then(Mono.just(failure(
                                ResendRegistrationOtpResultStatus.SYSTEM_FAILED,
                                WorkflowNextStep.VERIFY_OTP,
                                RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED,
                                RegistrationIdempotencyStartOutcome.STARTED))));
    }

    private Mono<RegistrationIdempotencyRecord> markCompleted(
            ResendRegistrationOtpCommand command,
            RegistrationRequestHash hash,
            ResendRegistrationOtpResult result) {
        return idempotencyPort.markCompleted(new RegistrationIdempotencyCompleteCommand(
                RegistrationIdempotencyAction.OTP_RESEND,
                command.idempotencyKey(),
                hash,
                snapshot(result),
                clock.instant(),
                command.idempotencyTtl()));
    }

    private Mono<RegistrationIdempotencyRecord> markFailed(
            ResendRegistrationOtpCommand command,
            RegistrationRequestHash hash) {
        return idempotencyPort.markFailed(new RegistrationIdempotencyFailCommand(
                RegistrationIdempotencyAction.OTP_RESEND,
                command.idempotencyKey(),
                hash,
                RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED,
                clock.instant(),
                command.idempotencyTtl()));
    }

    private RegistrationIdempotencyResponseSnapshot snapshot(ResendRegistrationOtpResult result) {
        String body = "{"
                + jsonField("nextStep", result.nextStep().name())
                + "," + jsonField("maskedDestination", result.maskedDestination())
                + "," + jsonNumberField("expiresInSeconds", result.expiresInSeconds())
                + "," + jsonNumberField("resendCooldownSeconds", result.resendCooldownSeconds())
                + "," + jsonNumberField("remainingResendAttempts", result.remainingResendAttempts())
                + "}";
        return new RegistrationIdempotencyResponseSnapshot("200", body, Map.of());
    }

    private String jsonField(String name, String value) {
        return "\"" + name + "\":" + (value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }

    private String jsonNumberField(String name, int value) {
        return "\"" + name + "\":" + value;
    }

    private ResendRegistrationOtpResult waitResult(RegistrationIdempotencyStartOutcome outcome) {
        return new ResendRegistrationOtpResult(
                ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(),
                outcome,
                null);
    }

    private ResendRegistrationOtpResult completedReplay(RegistrationIdempotencyStartResult start) {
        return new ResendRegistrationOtpResult(
                ResendRegistrationOtpResultStatus.COMPLETED_REPLAY,
                WorkflowNextStep.VERIFY_OTP,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(),
                start.outcome(),
                start.record() == null ? null : start.record().responseSnapshot());
    }

    private ResendRegistrationOtpResult failure(
            ResendRegistrationOtpResultStatus status,
            WorkflowNextStep nextStep,
            String code,
            RegistrationIdempotencyStartOutcome outcome) {
        return new ResendRegistrationOtpResult(
                status,
                nextStep,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(new WorkflowErrorDescriptor(
                        WorkflowErrorType.FORM,
                        code,
                        null,
                        List.of("PAGE"),
                        WorkflowErrorSeverity.ERROR,
                        SOURCE_SERVER)),
                outcome,
                null);
    }

    private String otpChallengeId(RegistrationOtpCreationResult otp) {
        Object value = firstOtpValue(otp, "otpChallengeId", "activeOtpChallengeId", "newOtpChallengeId", "challengeId", "id");
        if (value == null) {
            throw new IllegalStateException("OTP resend result does not contain a challenge id");
        }
        return String.valueOf(value);
    }

    private String rawOtpCode(RegistrationOtpCreationResult otp) {
        Object value = firstOtpValue(otp, "rawCode", "rawOtpCode", "otpCode", "code");
        if (value == null) {
            throw new IllegalStateException("OTP resend result does not contain a raw code for delivery");
        }
        return String.valueOf(value);
    }

    private Object firstOtpValue(RegistrationOtpCreationResult target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Object value = target.getClass().getMethod(methodName).invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor name. Do not log sensitive OTP values.
            }
        }
        return null;
    }
}
