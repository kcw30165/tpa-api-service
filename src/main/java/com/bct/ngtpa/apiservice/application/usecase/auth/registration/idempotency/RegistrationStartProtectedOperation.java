package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Registration Start business workflow with no idempotency persistence responsibility. */
public final class RegistrationStartProtectedOperation {
  private final RegistrationStartValidator validator;
  private final RegistrationLookupPort lookup;
  private final RegistrationSessionPort sessions;
  private final com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort authFlowSessions;
  private final RegistrationCsrPort csrf;
  private final RegistrationOtpPort otp;
  private final RegistrationMessagePort messages;
  private final AuthWorkflowPolicyPort policy;
  private final RegistrationCaptchaGateService captcha;
  private final Clock clock;

  public RegistrationStartProtectedOperation(
      RegistrationStartValidator validator,
      RegistrationLookupPort lookup,
      RegistrationSessionPort sessions,
      RegistrationCsrPort csrf,
      RegistrationOtpPort otp,
      RegistrationMessagePort messages,
      AuthWorkflowPolicyPort policy,
      RegistrationCaptchaGateService captcha,
      Clock clock) {
    this(validator, lookup, sessions, null, csrf, otp, messages, policy, captcha, clock);
  }

  public RegistrationStartProtectedOperation(
      RegistrationStartValidator validator,
      RegistrationLookupPort lookup,
      RegistrationSessionPort sessions,
      com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort authFlowSessions,
      RegistrationCsrPort csrf,
      RegistrationOtpPort otp,
      RegistrationMessagePort messages,
      AuthWorkflowPolicyPort policy,
      RegistrationCaptchaGateService captcha,
      Clock clock) {
    this.validator = Objects.requireNonNull(validator);
    this.lookup = Objects.requireNonNull(lookup);
    this.sessions = Objects.requireNonNull(sessions);
    this.authFlowSessions = authFlowSessions;
    this.csrf = Objects.requireNonNull(csrf);
    this.otp = Objects.requireNonNull(otp);
    this.messages = Objects.requireNonNull(messages);
    this.policy = policy;
    this.captcha = captcha;
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<StartRegistrationResult> execute(StartRegistrationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var validation =
        validator.validate(
            new RegistrationStartInput(
                command.identityType(),
                command.identityValue(),
                command.dateOfBirth(),
                command.email(),
                command.mobile(),
                command.captchaChallengeId()));
    if (!validation.valid()) {
      return Mono.just(
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
              validation.errors().stream()
                  .map(e -> new StartRegistrationError(e.errorKey(), e.target()))
                  .toList(),
              RegistrationIdempotencyStartOutcome.STARTED,
              null));
    }
    RegistrationStartCommand validated = validation.command();
    if (!captchaEnabled()) return lookup(command, validated);
    return consumeCaptcha(validated)
        .flatMap(
            error ->
                Mono.just(
                    failure(
                        StartRegistrationResultStatus.VALIDATION_FAILED,
                        error,
                        "captchaChallengeId")))
        .switchIfEmpty(Mono.defer(() -> lookup(command, validated)))
        .onErrorResume(
            ignored ->
                Mono.just(
                    failure(
                        StartRegistrationResultStatus.SYSTEM_FAILED,
                        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE,
                        "PAGE")));
  }

  private Mono<WorkflowErrorKey> consumeCaptcha(RegistrationStartCommand validated) {
    if (!hasText(validated.captchaChallengeId())) {
      return Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_REQUIRED);
    }
    if (captcha == null) return Mono.error(new IllegalStateException("captcha gate unavailable"));
    CaptchaChallengeId id;
    try {
      id = new CaptchaChallengeId(validated.captchaChallengeId());
    } catch (RuntimeException ignored) {
      return Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
    }
    return captcha
        .consume(new ConsumeCaptchaChallengeCommand(id, clock.instant()))
        .flatMap(
            result ->
                switch (result.outcome()) {
                  case CONSUMED -> Mono.empty();
                  case NOT_FOUND -> Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_NOTFOUND);
                  case NOT_VERIFIED -> Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
                  case EXPIRED -> Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_EXPIRED);
                  case LOCKED -> Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_LOCKED);
                  case ALREADY_CONSUMED ->
                      Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED);
                  case REFRESHED ->
                      Mono.just(RegistrationErrorKey.REGISTRATION_CAPTCHA_REFRESHFAILED);
                });
  }

  private Mono<StartRegistrationResult> lookup(
      StartRegistrationCommand command, RegistrationStartCommand validated) {
    var lookupCommand =
        new RegistrationLookupCommand(
            validated.identityType(),
            validated.identityValue(),
            validated.dateOfBirth(),
            validated.contactType(),
            validated.contactValue(),
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    return lookup
        .searchMember(lookupCommand)
        .flatMap(result -> handleLookup(command, validated, result));
  }

  private Mono<StartRegistrationResult> handleLookup(
      StartRegistrationCommand command,
      RegistrationStartCommand validated,
      RegistrationLookupResult found) {
    if (found.status() == RegistrationLookupStatus.NOT_ELIGIBLE) {
      return Mono.just(
          failure(
              StartRegistrationResultStatus.VERIFICATION_FAILED,
              RegistrationErrorKey.START_VERIFICATION_FAILED,
              "identityValue"));
    }
    var context =
        new RegistrationAuthFlowContext(
            validated.identityType(),
            validated.identityValue(),
            validated.dateOfBirth(),
            validated.contactType(),
            validated.contactValue());
    List<com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount>
        unregisteredAccounts =
            found.accounts().stream()
                .filter(account -> Boolean.FALSE.equals(account.registered()))
                .toList();
    if (unregisteredAccounts.isEmpty()) {
      return completeAlreadyRegisteredStart(validated, context);
    }
    return sessions
        .createRegistrationSession(validated.maskedDestination(), validated.contactType(), context)
        .flatMap(
            sessionId ->
                csrf.bindCsrfToken(sessionId)
                    .flatMap(
                        csrfToken ->
                            otp.createOtp(validated.contactType(), validated.contactValue())
                                .flatMap(
                                    created ->
                                        sessions
                                            .updateOtpChallenge(
                                                sessionId,
                                                created.activeOtpChallengeId(),
                                                validated.contactType(),
                                                validated.maskedDestination())
                                            .then(
                                                messages.deliverRegistrationOtp(
                                                    validated.contactType(),
                                                    validated.contactValue(),
                                                    created.otpCode(),
                                                    created.expiresInSeconds(),
                                                    command.acceptLanguage(),
                                                    command.correlationId()))
                                            .map(
                                                accepted ->
                                                    Boolean.TRUE.equals(accepted)
                                                        ? new StartRegistrationResult(
                                                            StartRegistrationResultStatus.STARTED,
                                                            WorkflowNextStep.VERIFY_OTP,
                                                            validated.maskedDestination(),
                                                            created.expiresInSeconds(),
                                                            created.resendCooldownSeconds(),
                                                            created.remainingResendAttempts(),
                                                            csrfToken,
                                                            sessionId,
                                                            List.of(),
                                                            List.of(),
                                                            RegistrationIdempotencyStartOutcome
                                                                .STARTED,
                                                            null)
                                                        : failure(
                                                            StartRegistrationResultStatus
                                                                .MESSAGE_DELIVERY_FAILED,
                                                            RegistrationErrorKey
                                                                .MESSAGE_DELIVERY_FAILED,
                                                            "email")))));
  }

  private Mono<StartRegistrationResult> completeAlreadyRegisteredStart(
      RegistrationStartCommand validated, RegistrationAuthFlowContext context) {
    if (authFlowSessions == null) {
      return Mono.error(new IllegalStateException("Auth-flow session port is required"));
    }
    return sessions
        .createRegistrationSession(validated.maskedDestination(), validated.contactType(), context)
        .flatMap(
            sessionId ->
                authFlowSessions
                    .loadSnapshot(
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow
                            .AuthFlowSessionId(sessionId))
                    .flatMap(
                        optional -> {
                          if (optional.isEmpty()) {
                            return Mono.error(
                                new IllegalStateException(
                                    "Registration auth-flow session was not available for completion"));
                          }
                          return authFlowSessions
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
                                    return Mono.just(
                                        new StartRegistrationResult(
                                            StartRegistrationResultStatus.REGISTRATION_COMPLETED,
                                            WorkflowNextStep.RETURN_TO_LOGIN,
                                            null,
                                            0,
                                            0,
                                            0,
                                            null,
                                            sessionId,
                                            List.of(
                                                com.bct.ngtpa.apiservice.application.dto.workflow
                                                    .RegistrationMessageKey.COMPLETION
                                                    .value()),
                                            List.of(),
                                            RegistrationIdempotencyStartOutcome.STARTED,
                                            null));
                                  });
                        }));
  }

  private StartRegistrationResult failure(
      StartRegistrationResultStatus status, WorkflowErrorKey key, String target) {
    return new StartRegistrationResult(
        status,
        WorkflowNextStep.NONE,
        null,
        0,
        0,
        0,
        null,
        null,
        List.of(),
        List.of(new StartRegistrationError(key, target)),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private boolean captchaEnabled() {
    if (policy == null
        || policy.registrationPolicy() == null
        || policy.registrationPolicy().preStartChallenge() == null) return false;
    return policy.registrationPolicy().preStartChallenge().enabled()
        && policy.registrationPolicy().preStartChallenge().type() == PreStartChallengeType.CAPTCHA;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
