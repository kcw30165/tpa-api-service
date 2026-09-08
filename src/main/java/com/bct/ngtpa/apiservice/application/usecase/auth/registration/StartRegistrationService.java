package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpSendRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

public class StartRegistrationService implements StartRegistrationUseCase {
  private final RegistrationStartValidator validator;
  private final RegistrationRequestHashGenerator requestHashGenerator;
  private final RegistrationIdempotencyPort idempotencyPort;
  private final RegistrationLookupPort lookupPort;
  private final RegistrationSessionPort sessionGateway;
  private final RegistrationCsrPort csrfGateway;
  private final RegistrationOtpPort otpGateway;
  private final RegistrationMessagePort messageGateway;
  private final AuthFlowSessionPort authFlowSessionPort;
  private final OtpChallengeStatePort otpChallengeStatePort;
  private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
  private final RegistrationCaptchaGateService registrationCaptchaGateService;
  private final Clock clock;

  public StartRegistrationService(
      RegistrationStartValidator validator,
      RegistrationRequestHashGenerator requestHashGenerator,
      RegistrationIdempotencyPort idempotencyPort,
      RegistrationLookupPort lookupPort,
      RegistrationSessionPort sessionGateway,
      RegistrationCsrPort csrfGateway,
      RegistrationOtpPort otpGateway,
      RegistrationMessagePort messageGateway,
      AuthFlowSessionPort authFlowSessionPort,
      OtpChallengeStatePort otpChallengeStatePort,
      Clock clock) {
    this(
        validator,
        requestHashGenerator,
        idempotencyPort,
        lookupPort,
        sessionGateway,
        csrfGateway,
        otpGateway,
        messageGateway,
        authFlowSessionPort,
        otpChallengeStatePort,
        null,
        null,
        clock);
  }

  public StartRegistrationService(
      RegistrationStartValidator validator,
      RegistrationRequestHashGenerator requestHashGenerator,
      RegistrationIdempotencyPort idempotencyPort,
      RegistrationLookupPort lookupPort,
      RegistrationSessionPort sessionGateway,
      RegistrationCsrPort csrfGateway,
      RegistrationOtpPort otpGateway,
      RegistrationMessagePort messageGateway,
      AuthFlowSessionPort authFlowSessionPort,
      OtpChallengeStatePort otpChallengeStatePort,
      AuthWorkflowPolicyPort authWorkflowPolicyPort,
      RegistrationCaptchaGateService registrationCaptchaGateService,
      Clock clock) {
    this.authFlowSessionPort = authFlowSessionPort;
    this.otpChallengeStatePort = otpChallengeStatePort;
    this.authWorkflowPolicyPort = authWorkflowPolicyPort;
    this.registrationCaptchaGateService = registrationCaptchaGateService;
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
    return idempotencyPort
        .startProcessing(
            new RegistrationIdempotencyStartCommand(
                RegistrationIdempotencyAction.REGISTRATION_START,
                command.idempotencyKey(),
                requestHash,
                clock.instant(),
                command.idempotencyTtl()))
        .flatMap(start -> handleIdempotency(command, requestHash, start));
  }

  private Mono<StartRegistrationResult> handleIdempotency(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      RegistrationIdempotencyStartResult start) {
    return switch (start.outcome()) {
      case STARTED -> continueAfterIdempotency(command, hash);
      case DUPLICATE_IN_PROGRESS ->
          Mono.just(
              result(
                  StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS,
                  WorkflowNextStep.WAIT,
                  SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS,
                  "Idempotency-Key",
                  start.outcome()));
      case COMPLETED_REPLAY -> replayCompletedRegistrationStartFromCurrentState(command, start);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          Mono.just(
              result(
                  StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
                  WorkflowNextStep.NONE,
                  SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED,
                  "Idempotency-Key",
                  start.outcome()));
      case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING ->
          Mono.just(
              result(
                  StartRegistrationResultStatus.SYSTEM_FAILED,
                  WorkflowNextStep.NONE,
                  SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE,
                  "PAGE",
                  start.outcome()));
    };
  }

  private Mono<StartRegistrationResult> continueAfterIdempotency(
      StartRegistrationCommand command, RegistrationRequestHash hash) {
    RegistrationStartValidationResult validation =
        validator.validate(
            new RegistrationStartInput(
                command.identityType(),
                command.identityValue(),
                command.dateOfBirth(),
                command.email(),
                command.mobile(),
                command.captchaChallengeId()));
    if (!validation.valid()) {
      var errors =
          validation.errors().stream()
              .map(e -> new StartRegistrationError(e.errorKey(), e.target()))
              .toList();
      var invalid =
          new StartRegistrationResult(
              StartRegistrationResultStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              null,
              0,
              0,
              0,
              null,
              null,
              List.of(),
              errors,
              RegistrationIdempotencyStartOutcome.STARTED,
              null);
      return markFailed(
              command,
              hash,
              com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey
                  .START_REQUEST_INVALID
                  .value())
          .thenReturn(invalid);
    }
    RegistrationStartCommand validated = validation.command();
    if (isCaptchaGateEnabled()) {
      return consumeRegistrationCaptcha(command, hash, validated)
          .switchIfEmpty(Mono.defer(() -> continueAfterCaptcha(command, hash, validated)))
          .onErrorResume(error -> gateSystemFailure(command, hash));
    }
    return continueAfterCaptcha(command, hash, validated);
  }

  private Mono<StartRegistrationResult> continueAfterCaptcha(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      RegistrationStartCommand validated) {
    var lookupCommand =
        new RegistrationLookupCommand(
            validated.identityType(),
            validated.identityValue(),
            validated.dateOfBirth(),
            validated.contactType(),
            validated.contactValue(),
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    return lookupPort
        .searchMember(lookupCommand)
        .flatMap(lookup -> handleLookup(command, hash, validated, lookup))
        .onErrorResume(
            error ->
                markFailed(
                        command,
                        hash,
                        com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey
                            .DOWNSTREAM_UNAVAILABLE
                            .value())
                    .then(Mono.error(error)));
  }

  private Mono<StartRegistrationResult> consumeRegistrationCaptcha(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      RegistrationStartCommand validated) {
    String captchaChallengeId = validated.captchaChallengeId();
    if (!hasText(captchaChallengeId)) {
      return captchaFailure(command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_REQUIRED);
    }
    CaptchaChallengeId challengeId;
    try {
      challengeId = new CaptchaChallengeId(captchaChallengeId);
    } catch (RuntimeException exception) {
      return captchaFailure(command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
    }
    if (registrationCaptchaGateService == null) {
      return gateSystemFailure(command, hash);
    }
    return registrationCaptchaGateService
        .consume(new ConsumeCaptchaChallengeCommand(challengeId, clock.instant()))
        .flatMap(
            result ->
                switch (result.outcome()) {
                  case CONSUMED -> Mono.empty();
                  case NOT_FOUND ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_NOTFOUND);
                  case NOT_VERIFIED ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
                  case EXPIRED ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_EXPIRED);
                  case LOCKED ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_LOCKED);
                  case ALREADY_CONSUMED ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED);
                  case REFRESHED ->
                      captchaFailure(
                          command, hash, RegistrationErrorKey.REGISTRATION_CAPTCHA_REFRESHFAILED);
                });
  }

  private Mono<StartRegistrationResult> captchaFailure(
      StartRegistrationCommand command, RegistrationRequestHash hash, WorkflowErrorKey errorKey) {
    return markFailed(command, hash, errorKey.value())
        .thenReturn(
            result(
                StartRegistrationResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.NONE,
                errorKey,
                "captchaChallengeId",
                RegistrationIdempotencyStartOutcome.STARTED));
  }

  private Mono<StartRegistrationResult> gateSystemFailure(
      StartRegistrationCommand command, RegistrationRequestHash hash) {
    return markFailed(command, hash, SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value())
        .thenReturn(
            result(
                StartRegistrationResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.NONE,
                SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE,
                "PAGE",
                RegistrationIdempotencyStartOutcome.STARTED));
  }

  private boolean isCaptchaGateEnabled() {
    if (authWorkflowPolicyPort == null) {
      return false;
    }
    var registrationPolicy = authWorkflowPolicyPort.registrationPolicy();
    if (registrationPolicy == null || registrationPolicy.preStartChallenge() == null) {
      return false;
    }
    return registrationPolicy.preStartChallenge().enabled()
        && registrationPolicy.preStartChallenge().type() == PreStartChallengeType.CAPTCHA;
  }

  private Mono<StartRegistrationResult> handleLookup(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      RegistrationStartCommand validated,
      RegistrationLookupResult lookup) {
    if (lookup.status() == RegistrationLookupStatus.NOT_ELIGIBLE) {
      var verificationFailure =
          result(
              StartRegistrationResultStatus.VERIFICATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.START_VERIFICATION_FAILED,
              "identityValue",
              RegistrationIdempotencyStartOutcome.STARTED,
              null);
      return markFailed(
              command,
              hash,
              com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey
                  .START_VERIFICATION_FAILED
                  .value())
          .thenReturn(verificationFailure);
    }
    RegistrationAuthFlowContext registrationContext =
        new RegistrationAuthFlowContext(
            validated.identityType(),
            validated.identityValue(),
            validated.dateOfBirth(),
            validated.contactType(),
            validated.contactValue());
    List<RegistrationLookupAccount> unregisteredAccounts =
        lookup.accounts().stream()
            .filter(account -> Boolean.FALSE.equals(account.registered()))
            .toList();
    if (unregisteredAccounts.isEmpty()) {
      return completeAlreadyRegisteredStart(command, hash, validated, registrationContext);
    }
    return sessionGateway
        .createRegistrationSession(
            validated.maskedDestination(), validated.contactType(), registrationContext)
        .flatMap(
            sessionId ->
                csrfGateway
                    .bindCsrfToken(sessionId)
                    .flatMap(
                        csrfToken ->
                            otpGateway
                                .createOtp(validated.contactType(), validated.contactValue())
                                .onErrorMap(
                                    RegistrationOtpSendRateLimitedException.class, error -> error)
                                .flatMap(
                                    otp ->
                                        sessionGateway
                                            .updateOtpChallenge(
                                                sessionId,
                                                otp.activeOtpChallengeId(),
                                                validated.contactType(),
                                                validated.maskedDestination())
                                            .then(
                                                messageGateway.deliverRegistrationOtp(
                                                    validated.contactType(),
                                                    validated.contactValue(),
                                                    otp.otpCode(),
                                                    otp.expiresInSeconds(),
                                                    command.acceptLanguage(),
                                                    command.correlationId()))
                                            .flatMap(
                                                accepted -> {
                                                  if (!Boolean.TRUE.equals(accepted)) {
                                                    return markFailed(
                                                            command,
                                                            hash,
                                                            com.bct.ngtpa.apiservice.application.dto
                                                                .workflow.RegistrationErrorKey
                                                                .MESSAGE_DELIVERY_FAILED
                                                                .value())
                                                        .thenReturn(
                                                            result(
                                                                StartRegistrationResultStatus
                                                                    .MESSAGE_DELIVERY_FAILED,
                                                                WorkflowNextStep.NONE,
                                                                RegistrationErrorKey
                                                                    .MESSAGE_DELIVERY_FAILED,
                                                                "email",
                                                                RegistrationIdempotencyStartOutcome
                                                                    .STARTED));
                                                  }
                                                  var started =
                                                      new StartRegistrationResult(
                                                          StartRegistrationResultStatus.STARTED,
                                                          WorkflowNextStep.VERIFY_OTP,
                                                          validated.maskedDestination(),
                                                          otp.expiresInSeconds(),
                                                          otp.resendCooldownSeconds(),
                                                          otp.remainingResendAttempts(),
                                                          csrfToken,
                                                          sessionId,
                                                          List.of(),
                                                          List.of(),
                                                          RegistrationIdempotencyStartOutcome
                                                              .STARTED,
                                                          null);
                                                  return markCompleted(command, hash, started)
                                                      .thenReturn(started);
                                                }))));
  }

  private Mono<StartRegistrationResult> completeAlreadyRegisteredStart(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      RegistrationStartCommand validated,
      RegistrationAuthFlowContext registrationContext) {
    return sessionGateway
        .createRegistrationSession(
            validated.maskedDestination(), validated.contactType(), registrationContext)
        .flatMap(
            sessionId ->
                authFlowSessionPort
                    .loadSnapshot(new AuthFlowSessionId(sessionId))
                    .flatMap(
                        optional -> {
                          if (optional.isEmpty()) {
                            return Mono.error(
                                new IllegalStateException(
                                    "Registration auth-flow session was not available for completion"));
                          }
                          return authFlowSessionPort
                              .completeRevisioned(optional.orElseThrow(), clock.instant())
                              .flatMap(
                                  mutation -> {
                                    if (mutation.outcome()
                                        != com.bct.ngtpa.apiservice.application.dto.session.authflow
                                            .AuthFlowSessionMutationOutcome.APPLIED) {
                                      return Mono.error(
                                          new IllegalStateException(
                                              "Registration auth-flow session completion failed"));
                                    }
                                    var completed =
                                        new StartRegistrationResult(
                                            StartRegistrationResultStatus.REGISTRATION_COMPLETED,
                                            WorkflowNextStep.RETURN_TO_LOGIN,
                                            null,
                                            0,
                                            0,
                                            0,
                                            null,
                                            sessionId,
                                            List.of("registration.completion"),
                                            List.of(),
                                            RegistrationIdempotencyStartOutcome.STARTED,
                                            null);
                                    return markCompleted(command, hash, completed)
                                        .thenReturn(completed);
                                  });
                        }));
  }

  private RegistrationRequestHash requestHash(StartRegistrationCommand command) {
    String email = command.email() == null ? "" : command.email().trim().toLowerCase(Locale.ROOT);
    String mobile = command.mobile() == null ? "" : command.mobile().trim();
    String contactType = !email.isBlank() ? "email" : (!mobile.isBlank() ? "mobile" : "none");
    String contactValue = !email.isBlank() ? email : mobile;
    return requestHashGenerator.hash(
        RegistrationIdempotencyAction.REGISTRATION_START,
        List.of(
            normalize(command.identityType()),
            normalize(command.identityValue()),
            normalize(command.dateOfBirth()),
            contactType,
            contactValue,
            normalize(command.captchaChallengeId())));
  }

  private Mono<Void> markCompleted(
      StartRegistrationCommand command,
      RegistrationRequestHash hash,
      StartRegistrationResult result) {
    var snapshot =
        new RegistrationIdempotencyResponseSnapshot(
            "200",
            "{\"status\":\""
                + result.status().name()
                + "\",\"nextStep\":\""
                + result.nextStep().name()
                + "\",\"maskedDestination\":\""
                + result.maskedDestination()
                + "\"}",
            Map.of());
    return idempotencyPort
        .markCompleted(
            new RegistrationIdempotencyCompleteCommand(
                RegistrationIdempotencyAction.REGISTRATION_START,
                command.idempotencyKey(),
                hash,
                snapshot,
                clock.instant(),
                command.idempotencyTtl()))
        .then();
  }

  private Mono<Void> markFailed(
      StartRegistrationCommand command, RegistrationRequestHash hash, String reasonCode) {
    return idempotencyPort
        .markFailed(
            new RegistrationIdempotencyFailCommand(
                RegistrationIdempotencyAction.REGISTRATION_START,
                command.idempotencyKey(),
                hash,
                reasonCode,
                clock.instant(),
                command.idempotencyTtl()))
        .then();
  }

  private StartRegistrationResult result(
      StartRegistrationResultStatus status,
      WorkflowNextStep nextStep,
      WorkflowErrorKey key,
      String target,
      RegistrationIdempotencyStartOutcome outcome) {
    return result(status, nextStep, key, target, outcome, null);
  }

  private StartRegistrationResult result(
      StartRegistrationResultStatus status,
      WorkflowNextStep nextStep,
      WorkflowErrorKey key,
      String target,
      RegistrationIdempotencyStartOutcome outcome,
      String message) {
    return new StartRegistrationResult(
        status,
        nextStep,
        null,
        0,
        0,
        0,
        null,
        null,
        List.of(),
        List.of(new StartRegistrationError(key, target, message)),
        outcome,
        null);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private StartRegistrationResult replayCompletedRegistrationStart(
      RegistrationIdempotencyStartResult idempotencyResult) {
    var snapshot =
        idempotencyResult == null || idempotencyResult.record() == null
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
        idempotencyResult == null
            ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY
            : idempotencyResult.outcome(),
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
        idempotencyResult == null
            ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY
            : idempotencyResult.outcome(),
        snapshot);
  }

  private RegistrationIdempotencyResponseSnapshot snapshotFor(StartRegistrationResult result) {
    return new RegistrationIdempotencyResponseSnapshot("200", replayBody(result), Map.of());
  }

  private String replayBody(StartRegistrationResult result) {
    return "{"
        + jsonField(
            "nextStep",
            result.nextStep() == null ? WorkflowNextStep.NONE.name() : result.nextStep().name())
        + ","
        + jsonField("maskedDestination", result.maskedDestination())
        + ","
        + jsonNumberField("expiresInSeconds", result.otpExpiresInSeconds())
        + ","
        + jsonNumberField("resendCooldownSeconds", result.resendCooldownSeconds())
        + ","
        + jsonNumberField("remainingResendAttempts", result.remainingResendAttempts())
        + ","
        + jsonField("csrfToken", result.csrfToken())
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
    var matcher =
        Pattern.compile(
                "\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\"])*)\\\")")
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
    var matcher =
        Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(-?\\d+)").matcher(body);
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

  private Mono<StartRegistrationResult> replayCompletedRegistrationStartFromCurrentState(
      StartRegistrationCommand command, RegistrationIdempotencyStartResult idempotencyResult) {
    String authFlowSessionId = command.authFlowSessionId();
    if (!hasTextReplay(authFlowSessionId)) {
      return Mono.just(replayUnavailable(idempotencyResult));
    }

    return authFlowSessionPort
        .load(new AuthFlowSessionId(authFlowSessionId))
        .flatMap(
            optionalSession -> {
              if (optionalSession == null || optionalSession.isEmpty()) {
                return Mono.just(replayUnavailable(idempotencyResult));
              }

              Object session = optionalSession.get();
              String otpChallengeId = invokeString(session, "activeOtpChallengeId");
              String maskedDestination = resolveMaskedDestination(session);

              if (!hasTextReplay(otpChallengeId)) {
                return Mono.just(replayUnavailable(idempotencyResult));
              }

              return otpChallengeStatePort
                  .findById(otpChallengeId)
                  .flatMap(
                      optionalChallenge -> {
                        if (optionalChallenge == null || optionalChallenge.isEmpty()) {
                          return Mono.just(replayUnavailable(idempotencyResult));
                        }

                        Object challenge = optionalChallenge.get();
                        Instant now = clock.instant();

                        int expiresInSeconds =
                            secondsUntil(now, invokeInstant(challenge, "expiresAt"));
                        int resendCooldownSeconds =
                            secondsUntil(
                                now,
                                plusSeconds(
                                    invokeInstant(challenge, "lastSentAt"),
                                    registrationOtpResendCooldownSeconds()));
                        int remainingResendAttempts =
                            Math.max(
                                0,
                                invokeInt(challenge, "maxResendAttempts")
                                    - invokeInt(challenge, "resendAttemptCount"));

                        return csrfGateway
                            .bindCsrfToken(authFlowSessionId)
                            .map(
                                csrfToken ->
                                    new StartRegistrationResult(
                                        StartRegistrationResultStatus.COMPLETED_REPLAY,
                                        WorkflowNextStep.VERIFY_OTP,
                                        maskedDestination,
                                        expiresInSeconds,
                                        resendCooldownSeconds,
                                        remainingResendAttempts,
                                        csrfToken,
                                        authFlowSessionId,
                                        List.of(),
                                        List.of(),
                                        idempotencyResult.outcome(),
                                        idempotencyResult.record() == null
                                            ? null
                                            : idempotencyResult.record().responseSnapshot()));
                      });
            });
  }

  private StartRegistrationResult replayUnavailable(
      RegistrationIdempotencyStartResult idempotencyResult) {
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
        idempotencyResult == null
            ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY
            : idempotencyResult.outcome(),
        idempotencyResult == null || idempotencyResult.record() == null
            ? null
            : idempotencyResult.record().responseSnapshot());
  }

  private int registrationOtpResendCooldownSeconds() {
    return 60;
  }

  private int secondsUntil(Instant now, Instant target) {
    if (now == null || target == null) {
      return 0;
    }
    return Math.max(0, (int) Duration.between(now, target).toSeconds());
  }

  private Instant plusSeconds(Instant source, int seconds) {
    return source == null ? null : source.plusSeconds(Math.max(0, seconds));
  }

  private String resolveMaskedDestination(Object session) {
    Object data = invokeObject(session, "data");
    String fromData = invokeString(data, "maskedDestination");
    if (hasTextReplay(fromData)) {
      return fromData;
    }
    return invokeString(session, "maskedDestination");
  }

  private Object invokeObject(Object target, String methodName) {
    if (target == null || methodName == null) {
      return null;
    }
    try {
      return target.getClass().getMethod(methodName).invoke(target);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private String invokeString(Object target, String methodName) {
    Object value = invokeObject(target, methodName);
    return value == null ? null : String.valueOf(value);
  }

  private Instant invokeInstant(Object target, String methodName) {
    Object value = invokeObject(target, methodName);
    return value instanceof Instant instant ? instant : null;
  }

  private int invokeInt(Object target, String methodName) {
    Object value = invokeObject(target, methodName);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private boolean hasTextReplay(String value) {
    return value != null && !value.isBlank();
  }
}
