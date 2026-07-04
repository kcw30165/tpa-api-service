package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class StartRegistrationService implements StartRegistrationUseCase {
    private final RegistrationStartValidator validator;
    private final RegistrationRequestHashGenerator requestHashGenerator;
    private final RegistrationIdempotencyPort idempotencyPort;
    private final RegistrationLookupPort lookupPort;
    private final RegistrationSessionGateway sessionGateway;
    private final RegistrationCsrfGateway csrfGateway;
    private final RegistrationOtpGateway otpGateway;
    private final RegistrationMessageGateway messageGateway;
    private final Clock clock;

    public StartRegistrationService(RegistrationStartValidator validator, RegistrationRequestHashGenerator requestHashGenerator,
            RegistrationIdempotencyPort idempotencyPort, RegistrationLookupPort lookupPort, RegistrationSessionGateway sessionGateway,
            RegistrationCsrfGateway csrfGateway, RegistrationOtpGateway otpGateway, RegistrationMessageGateway messageGateway, Clock clock) {
        this.validator = Objects.requireNonNull(validator);
        this.requestHashGenerator = Objects.requireNonNull(requestHashGenerator);
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort);
        this.lookupPort = Objects.requireNonNull(lookupPort);
        this.sessionGateway = Objects.requireNonNull(sessionGateway);
        this.csrfGateway = Objects.requireNonNull(csrfGateway);
        this.otpGateway = Objects.requireNonNull(otpGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
        this.clock = Objects.requireNonNull(clock);
    }

    public Mono<StartRegistrationResult> start(StartRegistrationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        RegistrationRequestHash requestHash = requestHash(command);
        return idempotencyPort.startProcessing(new RegistrationIdempotencyStartCommand(
                        RegistrationIdempotencyAction.REGISTRATION_START, command.idempotencyKey(), requestHash, clock.instant(), command.idempotencyTtl()))
                .flatMap(start -> handleIdempotency(command, requestHash, start));
    }

    private Mono<StartRegistrationResult> handleIdempotency(StartRegistrationCommand command, RegistrationRequestHash hash, RegistrationIdempotencyStartResult start) {
        return switch (start.outcome()) {
            case STARTED -> continueAfterIdempotency(command, hash);
            case DUPLICATE_IN_PROGRESS -> Mono.just(result(StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT, RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_DUPLICATE_IN_PROGRESS, "Idempotency-Key", start.outcome()));
            case COMPLETED_REPLAY -> Mono.just(replayCompletedRegistrationStart(start));
            case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(result(StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED, WorkflowNextStep.NONE, RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED, "Idempotency-Key", start.outcome()));
            case FAILED_EXISTING -> Mono.just(result(StartRegistrationResultStatus.SYSTEM_FAILED, WorkflowNextStep.NONE, RegistrationWorkflowErrorKeys.WORKFLOW_SYSTEM_UNAVAILABLE, "PAGE", start.outcome()));
        };
    }

    private Mono<StartRegistrationResult> continueAfterIdempotency(StartRegistrationCommand command, RegistrationRequestHash hash) {
        RegistrationStartValidationResult validation = validator.validate(new RegistrationStartInput(command.identityType(), command.identityValue(), command.dateOfBirth(), command.email(), command.mobile(), command.captchaChallengeId()));
        if (!validation.valid()) {
            var errors = validation.errors().stream().map(e -> new StartRegistrationError(e.errorKey(), e.target())).toList();
            var invalid = new StartRegistrationResult(StartRegistrationResultStatus.VALIDATION_FAILED, WorkflowNextStep.NONE, null, 0, 0, 0, null, null, List.of(), errors, RegistrationIdempotencyStartOutcome.STARTED, null);
            return markFailed(command, hash, "registration.validation.failed").thenReturn(invalid);
        }
        RegistrationStartCommand validated = validation.command();
        var lookupCommand = new RegistrationLookupCommand(validated.identityType(), validated.identityValue(), validated.dateOfBirth(), validated.contactType(), validated.contactValue());
        return lookupPort.searchUnregisteredMember(lookupCommand)
                .flatMap(lookup -> handleLookup(command, hash, validated, lookup))
                .onErrorResume(error -> markFailed(command, hash, "registration.lookup.failed").then(Mono.error(error)));
    }

    private Mono<StartRegistrationResult> handleLookup(StartRegistrationCommand command, RegistrationRequestHash hash, RegistrationStartCommand validated, RegistrationLookupResult lookup) {
        if (lookup.status() == RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE) {
            var verificationFailure = result(
                    StartRegistrationResultStatus.VERIFICATION_FAILED,
                    WorkflowNextStep.NONE,
                    RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED,
                    "identityValue",
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null);
            return markFailed(command, hash, "registration.verification.failed").thenReturn(verificationFailure);
        }
        if (lookup.status() == RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED) {
            var verificationFailure = result(
                    StartRegistrationResultStatus.VERIFICATION_FAILED,
                    WorkflowNextStep.NONE,
                    RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED,
                    "identityValue",
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null);
            return markFailed(command, hash, "registration.lookup.businessRejected").thenReturn(verificationFailure);
        }
        return sessionGateway.createRegistrationSession(validated.maskedDestination(), validated.contactType())
                .flatMap(sessionId -> csrfGateway.bindCsrfToken(sessionId)
                        .flatMap(csrfToken -> otpGateway.createOtp(validated.contactType(), validated.contactValue())
                                .flatMap(otp -> sessionGateway.updateOtpChallenge(sessionId, otp.activeOtpChallengeId(), validated.contactType(), validated.maskedDestination())
                                        .then(messageGateway.deliverRegistrationOtp(validated.contactType(), validated.contactValue(), otp.otpCode(), otp.expiresInSeconds(), command.acceptLanguage(), command.correlationId()))
                                        .flatMap(accepted -> {
                                            if (!Boolean.TRUE.equals(accepted)) {
                                                return markFailed(command, hash, "registration.messageDelivery.failed")
                                                        .thenReturn(result(StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED, WorkflowNextStep.NONE, RegistrationWorkflowErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED, "email", RegistrationIdempotencyStartOutcome.STARTED));
                                            }
                                            var started = new StartRegistrationResult(StartRegistrationResultStatus.STARTED, WorkflowNextStep.VERIFY_OTP, validated.maskedDestination(), otp.expiresInSeconds(), otp.resendCooldownSeconds(), otp.remainingResendAttempts(), csrfToken, sessionId, List.of(), List.of(), RegistrationIdempotencyStartOutcome.STARTED, null);
                                            return markCompleted(command, hash, started).thenReturn(started);
                                        }))));
    }


    private RegistrationRequestHash requestHash(StartRegistrationCommand command) {
        String email = command.email() == null ? "" : command.email().trim().toLowerCase(Locale.ROOT);
        String mobile = command.mobile() == null ? "" : command.mobile().trim();
        String contactType = !email.isBlank() ? "email" : (!mobile.isBlank() ? "mobile" : "none");
        String contactValue = !email.isBlank() ? email : mobile;
        return requestHashGenerator.hash(RegistrationIdempotencyAction.REGISTRATION_START,
                List.of(normalize(command.identityType()), normalize(command.identityValue()), normalize(command.dateOfBirth()), contactType, contactValue, normalize(command.captchaChallengeId())));
    }

    private Mono<Void> markCompleted(StartRegistrationCommand command, RegistrationRequestHash hash, StartRegistrationResult result) {
        var snapshot = new RegistrationIdempotencyResponseSnapshot("200", "{\"status\":\"" + result.status().name() + "\",\"nextStep\":\"" + result.nextStep().name() + "\",\"maskedDestination\":\"" + result.maskedDestination() + "\"}", Map.of());
        return idempotencyPort.markCompleted(new RegistrationIdempotencyCompleteCommand(RegistrationIdempotencyAction.REGISTRATION_START, command.idempotencyKey(), hash, snapshot, clock.instant(), command.idempotencyTtl())).then();
    }

    private Mono<Void> markFailed(StartRegistrationCommand command, RegistrationRequestHash hash, String reasonCode) {
        return idempotencyPort.markFailed(new RegistrationIdempotencyFailCommand(RegistrationIdempotencyAction.REGISTRATION_START, command.idempotencyKey(), hash, reasonCode, clock.instant(), command.idempotencyTtl())).then();
    }

    private StartRegistrationResult result(StartRegistrationResultStatus status, WorkflowNextStep nextStep, WorkflowErrorKey key, String target, RegistrationIdempotencyStartOutcome outcome) {
        return result(status, nextStep, key, target, outcome, null);
    }

    private StartRegistrationResult result(StartRegistrationResultStatus status, WorkflowNextStep nextStep, WorkflowErrorKey key, String target, RegistrationIdempotencyStartOutcome outcome, String message) {
        return new StartRegistrationResult(status, nextStep, null, 0, 0, 0, null, null, List.of(), List.of(new StartRegistrationError(key, target, message)), outcome, null);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }


    private StartRegistrationResult replayCompletedRegistrationStart(RegistrationIdempotencyStartResult idempotencyResult) {
        var snapshot = idempotencyResult == null || idempotencyResult.record() == null
                ? null
                : idempotencyResult.record().responseSnapshot();
        if (snapshot == null || !hasText(snapshot.serializedBody())) {
            return emptyCompletedReplay(idempotencyResult, snapshot);
        }
        String body = snapshot.serializedBody();
        return new StartRegistrationResult(
                StartRegistrationResultStatus.COMPLETED_REPLAY,
                parseNextStep(body),
                jsonString(body, "maskedDestination"),
                jsonInt(body, "expiresInSeconds"),
                jsonInt(body, "resendCooldownSeconds"),
                jsonInt(body, "remainingResendAttempts"),
                jsonString(body, "csrfToken"),
                null,
                List.of(),
                List.of(),
                idempotencyResult == null ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY : idempotencyResult.outcome(),
                snapshot);
    }

    private StartRegistrationResult emptyCompletedReplay(
            RegistrationIdempotencyStartResult idempotencyResult,
            RegistrationIdempotencyResponseSnapshot snapshot) {
        return new StartRegistrationResult(
                StartRegistrationResultStatus.COMPLETED_REPLAY,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(),
                idempotencyResult == null ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY : idempotencyResult.outcome(),
                snapshot);
    }

    private RegistrationIdempotencyResponseSnapshot snapshotFor(StartRegistrationResult result) {
        return new RegistrationIdempotencyResponseSnapshot("200", replayBody(result), Map.of());
    }

    private String replayBody(StartRegistrationResult result) {
        return "{"
                + jsonField("nextStep", result.nextStep() == null ? WorkflowNextStep.NONE.name() : result.nextStep().name())
                + "," + jsonField("maskedDestination", result.maskedDestination())
                + "," + jsonNumberField("expiresInSeconds", result.otpExpiresInSeconds())
                + "," + jsonNumberField("resendCooldownSeconds", result.resendCooldownSeconds())
                + "," + jsonNumberField("remainingResendAttempts", result.remainingResendAttempts())
                + "," + jsonField("csrfToken", result.csrfToken())
                + "}";
    }

    private String jsonField(String name, String value) {
        return "\"" + name + "\":" + (value == null ? "null" : "\"" + jsonEscape(value) + "\"");
    }

    private String jsonNumberField(String name, int value) {
        return "\"" + name + "\":" + value;
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private WorkflowNextStep parseNextStep(String body) {
        String raw = jsonString(body, "nextStep");
        if (!hasText(raw)) {
            return WorkflowNextStep.NONE;
        }
        try {
            return WorkflowNextStep.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return WorkflowNextStep.NONE;
        }
    }

    private String jsonString(String body, String field) {
        if (!hasText(body) || !hasText(field)) {
            return null;
        }
        var matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\"])*)\\\")")
                .matcher(body);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return null;
        }
        return jsonUnescape(matcher.group(2));
    }

    private int jsonInt(String body, String field) {
        if (!hasText(body) || !hasText(field)) {
            return 0;
        }
        var matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(-?\\d+)")
                .matcher(body);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String jsonUnescape(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!escaping) {
                if (ch == '\\') {
                    escaping = true;
                } else {
                    result.append(ch);
                }
                continue;
            }
            switch (ch) {
                case 'r' -> result.append('\r');
                case 'n' -> result.append('\n');
                case 't' -> result.append('\t');
                case '\\' -> result.append('\\');
                case '"' -> result.append('"');
                default -> result.append(ch);
            }
            escaping = false;
        }
        if (escaping) {
            result.append('\\');
        }
        return result.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
