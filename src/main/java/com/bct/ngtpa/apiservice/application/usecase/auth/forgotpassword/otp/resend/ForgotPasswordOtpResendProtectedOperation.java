package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/** Executes OTP_RESEND after shared idempotency ownership has been acquired. */
public final class ForgotPasswordOtpResendProtectedOperation {
  private static final String CHANNEL_EMAIL = "email";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ForgotPasswordOtpResendProtectedOperation.class);

  private static final String EXPIRES_AT = "forgotPasswordOtpExpiresAt";
  private static final String RESEND_AT = "forgotPasswordOtpResendAvailableAt";
  private static final String RESEND_COUNT = "forgotPasswordOtpSuccessfulResendCount";
  private static final String PENDING_REF = "forgotPasswordOtpPendingResendRef";
  private static final String PENDING_CANDIDATE = "forgotPasswordOtpPendingCandidateId";
  private static final String PENDING_PREVIOUS = "forgotPasswordOtpPendingPreviousChallengeId";
  private final SecureForgotPasswordOtpCommitRefGenerator refs;
  private final AuthFlowSessionMutationLockPort locks;
  private final AuthFlowSessionPort sessions;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final EvaluateForgotPasswordOtpResendUseCase foundation;
  private final OtpChallengePort otp;
  private final OtpChallengeStatePort challengeState;
  private final MessageDeliveryPort delivery;
  private final MaskingService masker;
  private final ForgotPasswordOtpCommitStatePort commitStates;
  private final ForgotPasswordOtpCommitRetentionPolicy retention;
  private final NoticeTemplateRegistryPort registry;
  private final ForgotPasswordInitialOtpPolicy otpPolicy;
  private final CsrfTokenValidationService csrfTokens =
      new CsrfTokenValidationService(new Sha256CsrfTokenHasher());
  private final OriginRefererValidator origins = new OriginRefererValidator();
  private final List<String> allowedOrigins;
  private final Clock clock;
  private final Duration idempotencyTtl;
  private final Duration lockLease;

  public ForgotPasswordOtpResendProtectedOperation(
      SecureForgotPasswordOtpCommitRefGenerator refs,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      EvaluateForgotPasswordOtpResendUseCase foundation,
      OtpChallengePort otp,
      OtpChallengeStatePort challengeState,
      MessageDeliveryPort delivery,
      MaskingService masker,
      ForgotPasswordOtpCommitStatePort commitStates,
      ForgotPasswordOtpCommitRetentionPolicy retention,
      NoticeTemplateRegistryPort registry,
      ForgotPasswordInitialOtpPolicy otpPolicy,
      Clock clock,
      Duration idempotencyTtl,
      Duration lockLease,
      List<String> allowedOrigins) {
    this.refs = Objects.requireNonNull(refs);
    this.locks = Objects.requireNonNull(locks);
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.foundation = Objects.requireNonNull(foundation);
    this.otp = Objects.requireNonNull(otp);
    this.challengeState = Objects.requireNonNull(challengeState);
    this.delivery = Objects.requireNonNull(delivery);
    this.masker = Objects.requireNonNull(masker);
    this.commitStates = Objects.requireNonNull(commitStates);
    this.retention = Objects.requireNonNull(retention);
    this.registry = Objects.requireNonNull(registry);
    this.otpPolicy = Objects.requireNonNull(otpPolicy);
    this.clock = Objects.requireNonNull(clock);
    this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl);
    this.lockLease = Objects.requireNonNull(lockLease);
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  public Mono<ForgotPasswordOtpResendProtectedExecutionResult> execute(
      ResendForgotPasswordOtpCommand command) {
    return Mono.defer(
        () -> {
          IncidentCollector incidents = new IncidentCollector();
          incidents.stage(ForgotPasswordOtpResendIncident.PENDING_REFERENCE_GENERATION);
          String pendingRef;
          try {
            pendingRef = refs.generate();
          } catch (RuntimeException cause) {
            incidents.recordFailure(command, cause);
            return Mono.just(
                execution(
                    new ResendForgotPasswordOtpResult(
                        ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null),
                    null,
                    incidents));
          }
          incidents.stage(ForgotPasswordOtpResendIncident.LOCK_ACQUISITION);
          return locks
              .acquire(command.sessionId(), lockLease)
              .flatMap(
                  handle ->
                      handle.acquired()
                          ? locked(command, pendingRef, handle, incidents)
                          : Mono.just(
                              execution(
                                  new ResendForgotPasswordOtpResult(
                                      ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                                      null,
                                      null,
                                      null,
                                      null,
                                      null),
                                  null,
                                  incidents)))
              .onErrorResume(
                  cause -> {
                    incidents.recordFailure(command, cause);
                    return Mono.just(
                        execution(
                            new ResendForgotPasswordOtpResult(
                                ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                                null,
                                null,
                                null,
                                null,
                                null),
                            null,
                            incidents));
                  });
        });
  }

  private Mono<Void> saveCommitState(
      ResendForgotPasswordOtpCommand command,
      String reference,
      ForgotPasswordOtpCommitState state,
      Instant now,
      Instant challengeExpiry,
      IncidentCollector incidents) {
    incidents.stage(ForgotPasswordOtpResendIncident.COMMIT_STATE);
    Duration ttl = retention.ttl(now, challengeExpiry, now.plus(idempotencyTtl));
    return commitStates
        .saveIfAbsent(reference, state, ttl)
        .flatMap(saved -> commitStates.load(reference))
        .flatMap(
            found ->
                found.isPresent() && found.get().equals(state)
                    ? Mono.<Void>empty()
                    : Mono.<Void>error(
                        new IllegalStateException("OTP_RESEND commit state conflict")))
        .doOnError(
            cause ->
                incidents.recordFailure(
                    command, cause, ForgotPasswordOtpResendIncident.COMMIT_STATE));
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> locked(
      ResendForgotPasswordOtpCommand command,
      String pendingRef,
      SessionLockHandle handle,
      IncidentCollector incidents) {
    incidents.stage(ForgotPasswordOtpResendIncident.SESSION_READ);
    Mono<ForgotPasswordOtpResendProtectedExecutionResult> work =
        sessions
            .loadSnapshot(command.sessionId())
            .flatMap(
                optional -> {
                  if (optional.isEmpty()) {
                    return Mono.just(
                        new ForgotPasswordOtpResendProtectedExecutionResult(
                            new ResendForgotPasswordOtpResult(
                                ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID,
                                null,
                                null,
                                null,
                                null,
                                null),
                            null));
                  }
                  AuthFlowSessionSnapshot snapshot = optional.get();
                  return validateEligibility(command, handle, snapshot, incidents)
                      .flatMap(
                          validated -> {
                            if (validated.status()
                                != ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE) {
                              return Mono.just(
                                execution(
                                      mapEligibility(
                                          validated,
                                          validated.maskedDestination(),
                                          validated.expiresInSeconds()),
                                    null,
                                    incidents));
                            }
                            return createCandidateAndDeliver(
                              command, pendingRef, snapshot, validated, incidents);
                          });
                })
            .onErrorResume(
                cause -> {
                  if (!(cause instanceof DeliveryOutcomeUnknownException)) {
                    incidents.recordFailure(command, cause);
                  }
                  return Mono.just(
                    execution(
                        new ResendForgotPasswordOtpResult(
                            ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                            null,
                            null,
                            null,
                            null,
                            null),
                        null,
                        incidents));
                });
    return Mono.usingWhen(
        Mono.just(handle),
        ignored -> work,
        ignored -> locks.release(handle).onErrorReturn(false).then(),
        (ignored, error) -> locks.release(handle).onErrorReturn(false).then(),
        ignored -> locks.release(handle).onErrorReturn(false).then());
  }

  private Mono<ValidatedState> validateEligibility(
      ResendForgotPasswordOtpCommand command,
      SessionLockHandle handle,
      AuthFlowSessionSnapshot snapshot,
      IncidentCollector incidents) {
    AuthFlowSession session = snapshot.session();
    Instant now = Instant.now(clock);
    if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD
        || session.status() != AuthFlowStatus.OTP_SENT
        || session.isTerminal()
        || session.isExpired(now)) {
      return Mono.just(
          ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID));
    }
    if (!csrfTokens.validate(command.csrfToken(), session.csrfTokenHash()).valid()) {
      return Mono.just(
          ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, true));
    }
    if (!origins.validate(command.origin(), command.referer(), allowedOrigins).valid()) {
      return Mono.just(
          ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, true));
    }
    ForgotPasswordAuthFlowContext context;
    try {
      incidents.stage(ForgotPasswordOtpResendIncident.CONTEXT_UNPROTECT);
      context = decrypt(session);
    } catch (RuntimeException ex) {
      incidents.recordFailure(command, ex);
      return Mono.just(
          ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID));
    }
    if (blank(context.contactChannel())
        || blank(context.protectedDestination())
        || blank(context.maskedDestination())
        || !Objects.equals(
            CHANNEL_EMAIL.equalsIgnoreCase(context.contactChannel())
                ? masker.maskEmail(context.protectedDestination())
                : masker.maskMobile(context.protectedDestination()),
            context.maskedDestination())
        || !Objects.equals(session.data().maskedDestination(), context.maskedDestination())) {
      return Mono.just(
          ValidatedState.invalid(
              ForgotPasswordOtpResendEligibilityStatus.CHALLENGE_BINDING_INVALID));
    }
    if (blank(session.activeOtpChallengeId())) {
      return Mono.just(
          ValidatedState.invalid(
              ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE,
              false,
              context.maskedDestination(),
              null));
    }
    incidents.stage(ForgotPasswordOtpResendIncident.CHALLENGE_READ);
    return challengeState
        .findById(session.activeOtpChallengeId())
        .flatMap(
            optionalChallenge -> {
              if (optionalChallenge.isEmpty()) {
                return Mono.just(
                    ValidatedState.invalid(
                        ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE,
                        false,
                        context.maskedDestination(),
                        null));
              }
              OtpChallenge authoritative = optionalChallenge.get();
              if (authoritative.scenario() != OtpScenario.FORGOT_PASSWORD
                  || !Objects.equals(
                      authoritative.workflowRef(), session.authFlowSessionId().value())
                  || !Objects.equals(
                      authoritative.destinationHash(), sha256(context.protectedDestination()))) {
                return Mono.just(
                    ValidatedState.invalid(
                        ForgotPasswordOtpResendEligibilityStatus.CHALLENGE_BINDING_INVALID,
                        false,
                        context.maskedDestination(),
                        secondsUntil(now, authoritative.expiresAt())));
              }
              int successfulResendCount =
                  readCount(command, session.data().attributes(), incidents);
              incidents.stage(ForgotPasswordOtpResendIncident.FOUNDATION);
              return foundation
                  .evaluate(
                      new ForgotPasswordOtpResendCommand(
                          handle,
                          session,
                          authoritative,
                          context.phase(),
                          successfulResendCount,
                          command.idempotencyKey(),
                          now))
                  .map(
                      foundationResult ->
                          new ValidatedState(
                              foundationResult.status(),
                              context,
                              authoritative,
                              successfulResendCount,
                              foundationResult.retryAfterSeconds(),
                              context.maskedDestination(),
                              secondsUntil(now, authoritative.expiresAt()),
                              false))
                  .doOnNext(
                      foundationResult -> {
                        if (foundationResult.status()
                                == ForgotPasswordOtpResendEligibilityStatus.SYSTEM_FAILED) {
                              incidents.record(ForgotPasswordOtpResendIncident.FOUNDATION);
                        }
                      })
                  .onErrorResume(
                      cause -> {
                        incidents.recordFailure(command, cause);
                        return Mono.just(
                            new ValidatedState(
                                ForgotPasswordOtpResendEligibilityStatus.SYSTEM_FAILED,
                                context,
                                authoritative,
                                successfulResendCount,
                                null,
                                context.maskedDestination(),
                                secondsUntil(now, authoritative.expiresAt()),
                                false));
                      });
            })
        .onErrorResume(
            cause -> {
              incidents.recordFailure(command, cause);
              return Mono.just(
                  ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SYSTEM_FAILED));
            });
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> createCandidateAndDeliver(
      ResendForgotPasswordOtpCommand command,
      String pendingRef,
      AuthFlowSessionSnapshot snapshot,
      ValidatedState validated,
      IncidentCollector incidents) {
    Instant now = Instant.now(clock);
    OtpChannel channel =
        CHANNEL_EMAIL.equalsIgnoreCase(validated.context.contactChannel())
            ? OtpChannel.EMAIL
            : OtpChannel.SMS;
    MessageDeliveryChannel deliveryChannel =
        CHANNEL_EMAIL.equalsIgnoreCase(validated.context.contactChannel())
            ? MessageDeliveryChannel.EMAIL
            : MessageDeliveryChannel.SMS;
    incidents.stage(ForgotPasswordOtpResendIncident.TEMPLATE_RESOLUTION);
    NoticeTemplateDefinition definition =
        registry.resolve(
            MessageDeliveryType.FORGOT_PASSWORD_OTP, deliveryChannel, command.language());
    incidents.stage(ForgotPasswordOtpResendIncident.CHALLENGE_CREATION);
    return otp.createChallenge(
            new CreateOtpChallengeCommand(
                OtpScenario.FORGOT_PASSWORD,
                snapshot.session().authFlowSessionId().value(),
                channel,
                sha256(validated.context.protectedDestination()),
                otpPolicy.length(),
                otpPolicy.numericOnly(),
                otpPolicy.ttlSeconds(),
                otpPolicy.maxVerifyAttempts(),
                otpPolicy.maxResendAttempts(),
                now))
        .flatMap(
            candidate ->
                stagePending(
                        command,
                        snapshot,
                        validated.context,
                        validated.authoritativeChallenge,
                        candidate.otpChallengeId(),
                        pendingRef,
                        now,
                        incidents)
                    .flatMap(
                        stagedSnapshot ->
                            {
                              incidents.stage(ForgotPasswordOtpResendIncident.MESSAGE_DELIVERY);
                              return delivery
                                  .deliver(
                                      message(
                                          command,
                                          validated.context,
                                          candidate.rawCode(),
                                          deliveryChannel,
                                          definition))
                                  .flatMap(
                                      deliveryResult ->
                                          transition(
                                              command,
                                              stagedSnapshot,
                                              validated,
                                              candidate,
                                              pendingRef,
                                              deliveryResult,
                                              now,
                                              incidents));
                            }));
  }

  private Mono<AuthFlowSessionSnapshot> stagePending(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot snapshot,
      ForgotPasswordAuthFlowContext context,
      OtpChallenge authoritative,
      String candidateId,
      String pendingRef,
      Instant now,
      IncidentCollector incidents) {
    Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
    attrs.put(PENDING_REF, pendingRef);
    attrs.put(PENDING_CANDIDATE, candidateId);
    attrs.put(PENDING_PREVIOUS, authoritative.otpChallengeId());
    return mutateSession(
        command, snapshot, snapshot.session().activeOtpChallengeId(), context, attrs, now, incidents);
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> transition(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot stagedSnapshot,
      ValidatedState validated,
      CreateOtpChallengeResult candidate,
      String pendingRef,
      MessageDeliveryResult deliveryResult,
      Instant now,
      IncidentCollector incidents) {
    MessageDeliveryStatus status = deliveryResult.status();
    if (status == MessageDeliveryStatus.ACCEPTED) {
      return accepted(command, stagedSnapshot, validated, candidate, pendingRef, now, incidents);
    }
    if (status == MessageDeliveryStatus.FAILED) {
      incidents.record(ForgotPasswordOtpResendIncident.MESSAGE_DELIVERY);
      return failed(
          command, stagedSnapshot, validated.context, candidate.otpChallengeId(), now, incidents);
    }
    incidents.record(ForgotPasswordOtpResendIncident.UNKNOWN_DELIVERY);
    return unknown(
        command, stagedSnapshot, validated.context, candidate.otpChallengeId(), now, incidents);
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> accepted(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot stagedSnapshot,
      ValidatedState validated,
      CreateOtpChallengeResult candidate,
      String pendingRef,
      Instant now,
      IncidentCollector incidents) {
    Map<String, String> attrs = new LinkedHashMap<>(stagedSnapshot.session().data().attributes());
    attrs.put(EXPIRES_AT, candidate.expiresAt().toString());
    attrs.put(RESEND_AT, now.plusSeconds(otpPolicy.resendCooldownSeconds()).toString());
    attrs.put(RESEND_COUNT, Integer.toString(validated.successfulResendCount + 1));
    ForgotPasswordAuthFlowContext context =
        validated.context.withPhase(ForgotPasswordProtectedPhase.OTP_SENT);
    return mutateSession(
            command, stagedSnapshot, candidate.otpChallengeId(), context, attrs, now, incidents)
        .flatMap(
            updatedSnapshot ->
                invalidateChallenge(command, validated.authoritativeChallenge, now, incidents)
                    .then(
                        saveCommitState(
                            command,
                            pendingRef,
                            new ForgotPasswordOtpCommitState(
                                updatedSnapshot.session().authFlowSessionId().value(),
                                candidate.otpChallengeId(),
                                now,
                                candidate.expiresAt()),
                            now,
                            candidate.expiresAt(),
                            incidents))
                    .thenReturn(
                        execution(
                            new ResendForgotPasswordOtpResult(
                                ResendForgotPasswordOtpStatus.RESENT,
                                validated.context.maskedDestination(),
                                candidate.expiresInSeconds(),
                                otpPolicy.resendCooldownSeconds(),
                                remainingResendAttempts(validated.successfulResendCount + 1),
                                null),
                            new ProtectedReplayReference(pendingRef),
                            incidents)));
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> failed(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot stagedSnapshot,
      ForgotPasswordAuthFlowContext context,
      String candidateId,
      Instant now,
      IncidentCollector incidents) {
    return invalidateChallenge(command, candidateId, now, incidents)
        .then(clearPending(command, stagedSnapshot, context, now, incidents))
        .thenReturn(
            execution(
                new ResendForgotPasswordOtpResult(
                    ResendForgotPasswordOtpStatus.DELIVERY_FAILED,
                    context.maskedDestination(),
                    null,
                    null,
                    null,
                    null),
                null,
                incidents));
  }

  private Mono<ForgotPasswordOtpResendProtectedExecutionResult> unknown(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot stagedSnapshot,
      ForgotPasswordAuthFlowContext context,
      String candidateId,
      Instant now,
      IncidentCollector incidents) {
    return invalidateChallenge(command, candidateId, now, incidents)
        .then(
            updatePhase(
                command,
                stagedSnapshot,
                context.withPhase(ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN),
                now,
                incidents))
        .then(
            Mono.error(
                new DeliveryOutcomeUnknownException()));
  }

  private Mono<AuthFlowSessionSnapshot> clearPending(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot snapshot,
      ForgotPasswordAuthFlowContext context,
      Instant now,
      IncidentCollector incidents) {
    Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
    attrs.remove(PENDING_REF);
    attrs.remove(PENDING_CANDIDATE);
    attrs.remove(PENDING_PREVIOUS);
    return mutateSession(
        command,
        snapshot,
        snapshot.session().activeOtpChallengeId(),
        context,
        attrs,
        now,
        incidents);
  }

  private Mono<AuthFlowSessionSnapshot> updatePhase(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot snapshot,
      ForgotPasswordAuthFlowContext context,
      Instant now,
      IncidentCollector incidents) {
    Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
    attrs.remove(PENDING_REF);
    attrs.remove(PENDING_CANDIDATE);
    attrs.remove(PENDING_PREVIOUS);
    return mutateSession(
        command,
        snapshot,
        snapshot.session().activeOtpChallengeId(),
        context,
        attrs,
        now,
        incidents);
  }

  private Mono<AuthFlowSessionSnapshot> mutateSession(
      ResendForgotPasswordOtpCommand command,
      AuthFlowSessionSnapshot snapshot,
      String activeChallengeId,
      ForgotPasswordAuthFlowContext context,
      Map<String, String> attrs,
      Instant now,
      IncidentCollector incidents) {
    incidents.stage(ForgotPasswordOtpResendIncident.CONTEXT_PROTECTION);
    ForgotPasswordAuthFlowEncryptedContext encrypted = protector.protect(context);
    attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
    attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
    attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());
    AuthFlowSession session = snapshot.session();
    AuthFlowSession updated =
        new AuthFlowSession(
            session.authFlowSessionId(),
            session.scenario(),
            AuthFlowStatus.OTP_SENT,
            session.csrfTokenHash(),
            activeChallengeId,
            new AuthFlowSessionData(context.contactChannel(), context.maskedDestination(), attrs),
            session.createdAt(),
            now,
            session.expiresAt());
    incidents.stage(ForgotPasswordOtpResendIncident.SESSION_MUTATION);
    return sessions
        .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(snapshot, updated))
        .flatMap(
            result ->
                result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                    ? Mono.just(result.snapshot())
                    : Mono.error(
                        new IllegalStateException(
                            "Auth flow session resend mutation failed: " + result.outcome())))
        .doOnError(
            cause ->
                incidents.recordFailure(
                    command, cause, ForgotPasswordOtpResendIncident.SESSION_MUTATION));
  }

  private Mono<Void> invalidateChallenge(
      ResendForgotPasswordOtpCommand command,
      OtpChallenge challenge,
      Instant now,
      IncidentCollector incidents) {
    incidents.stage(ForgotPasswordOtpResendIncident.CHALLENGE_MUTATION);
    OtpChallenge updated =
        new OtpChallenge(
            challenge.otpChallengeId(),
            challenge.scenario(),
            challenge.workflowRef(),
            challenge.channel(),
            challenge.destinationHash(),
            challenge.codeHash(),
            OtpStatus.INVALIDATED,
            challenge.failedAttemptCount(),
            challenge.maxVerifyAttempts(),
            challenge.resendAttemptCount(),
            challenge.maxResendAttempts(),
            challenge.createdAt(),
            challenge.expiresAt(),
            challenge.lastSentAt(),
            now,
            challenge.verifiedAt());
    return challengeState
        .update(updated)
        .doOnError(
            cause ->
                incidents.recordFailure(
                    command, cause, ForgotPasswordOtpResendIncident.CHALLENGE_MUTATION));
  }

  private Mono<Void> invalidateChallenge(
      ResendForgotPasswordOtpCommand command,
      String challengeId,
      Instant now,
      IncidentCollector incidents) {
    incidents.stage(ForgotPasswordOtpResendIncident.CHALLENGE_READ);
    return challengeState
        .findById(challengeId)
        .doOnError(
            cause ->
                incidents.recordFailure(
                    command, cause, ForgotPasswordOtpResendIncident.CHALLENGE_READ))
        .flatMap(
            optional ->
                optional
                    .map(challenge -> invalidateChallenge(command, challenge, now, incidents))
                    .orElseGet(Mono::empty));
  }

  private MessageDeliveryCommand message(
      ResendForgotPasswordOtpCommand command,
      ForgotPasswordAuthFlowContext context,
      String rawCode,
      MessageDeliveryChannel channel,
      NoticeTemplateDefinition definition) {
    String language = command.language();
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("code", rawCode);
    variables.put("ttlValue", Integer.toString(Math.max(1, otpPolicy.ttlSeconds() / 60)));
    variables.put("ttlUnit", definition.ttlUnit());
    if (context.trustCode() != null) {
      variables.put("trustCode", context.trustCode());
    }
    if (context.schemeName() != null) {
      variables.put("schemeName", context.schemeName());
    }
    String schemeNameChi = resolveSchemeNameChi(context);
    if (schemeNameChi != null) {
      variables.put("schemeNameChi", schemeNameChi);
    }
    return new MessageDeliveryCommand(
        MessageDeliveryType.FORGOT_PASSWORD_OTP,
        MessageDeliveryScenario.FORGOT_PASSWORD,
        channel,
        context.protectedDestination(),
        definition.subject(),
        definition.body(),
        null,
        Map.copyOf(variables),
        language,
        command.requestId());
  }

  /**
   * Mirrors {@code LoginOtpResendService.resolveSchemeNameChi} / {@code
   * UsernameRecoveryRequestProtectedOperation.resolveSchemeNameChi} (plan decision, Nick,
   * 2026-10-07; applied to Forgot Password per Nick's instruction on 2026-10-08 to include the same
   * rule now rather than defer it): a blank {@code schemeNameChi} falls back to {@code schemeName},
   * rather than rendering an empty Chinese scheme name into a live member message.
   */
  private static String resolveSchemeNameChi(ForgotPasswordAuthFlowContext context) {
    String schemeNameChi = context.schemeNameChi();
    return (schemeNameChi == null || schemeNameChi.isBlank())
        ? context.schemeName()
        : schemeNameChi;
  }

  private ResendForgotPasswordOtpResult mapEligibility(
      ValidatedState validated, String maskedDestination, Integer expiresInSeconds) {
    return switch (validated.status()) {
      case COOLDOWN_ACTIVE ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
              maskedDestination,
              expiresInSeconds,
              null,
              remainingResendAttempts(validated.successfulResendCount),
              validated.retryAfterSeconds());
      case RESEND_LIMIT_REACHED ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED,
              maskedDestination,
              expiresInSeconds,
              null,
              0,
              null);
      case SESSION_INVALID, CHALLENGE_BINDING_INVALID, INVALID_PHASE, NO_ACTIVE_CHALLENGE ->
          validated.securityRejected()
              ? new ResendForgotPasswordOtpResult(
                  ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null, null, null, null)
              : new ResendForgotPasswordOtpResult(
                  ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID, null, null, null, null, null);
      case UNKNOWN_OUTCOME_BLOCKED ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME,
              maskedDestination,
              expiresInSeconds,
              null,
              remainingResendAttempts(validated.successfulResendCount),
              null);
      default ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    };
  }

  private Integer remainingResendAttempts(int successfulResendCount) {
    return Math.max(0, otpPolicy.maxResendAttempts() - Math.max(0, successfulResendCount));
  }

  private ForgotPasswordAuthFlowContext decrypt(AuthFlowSession session) {
    Map<String, String> attributes =
        session.data() == null || session.data().attributes() == null
            ? Map.of()
            : session.data().attributes();
    return protector.unprotect(
        new ForgotPasswordAuthFlowEncryptedContext(
            attributes.get(CONTEXT_CIPHERTEXT),
            attributes.get(CONTEXT_KEY_VERSION),
            attributes.getOrDefault(
                CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)));
  }

  private int readCount(
      ResendForgotPasswordOtpCommand command,
      Map<String, String> attributes,
      IncidentCollector incidents) {
    if (attributes == null) {
      return 0;
    }
    String value = attributes.get(RESEND_COUNT);
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(0, parsed);
    } catch (RuntimeException invalid) {
      incidents.recordMalformedCounter(command);
      return 0;
    }
  }

  private Integer secondsUntil(Instant now, Instant end) {
    if (now == null || end == null) {
      return null;
    }
    long seconds = Math.max(0L, Duration.between(now, end).toSeconds());
    return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds));
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("forgot password resend hash unavailable");
    }
  }

  private static ForgotPasswordOtpResendProtectedExecutionResult execution(
      ResendForgotPasswordOtpResult result,
      ProtectedReplayReference replayReference,
      IncidentCollector incidents) {
    return new ForgotPasswordOtpResendProtectedExecutionResult(
        result, replayReference, incidents.snapshot());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private record ValidatedState(
      ForgotPasswordOtpResendEligibilityStatus status,
      ForgotPasswordAuthFlowContext context,
      OtpChallenge authoritativeChallenge,
      int successfulResendCount,
      Integer retryAfterSeconds,
      String maskedDestination,
      Integer expiresInSeconds,
      boolean securityRejected) {
    static ValidatedState invalid(ForgotPasswordOtpResendEligibilityStatus status) {
      return invalid(status, false, null, null);
    }

    static ValidatedState invalid(
        ForgotPasswordOtpResendEligibilityStatus status, boolean securityRejected) {
      return invalid(status, securityRejected, null, null);
    }

    static ValidatedState invalid(
        ForgotPasswordOtpResendEligibilityStatus status,
        boolean securityRejected,
        String maskedDestination,
        Integer expiresInSeconds) {
      return new ValidatedState(
          status, null, null, 0, null, maskedDestination, expiresInSeconds, securityRejected);
    }
  }

  private static final class IncidentCollector {
    private final List<ForgotPasswordOtpResendIncident> incidents = new ArrayList<>();
    private final Set<Throwable> observedFailures =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private ForgotPasswordOtpResendIncident stage =
        ForgotPasswordOtpResendIncident.LOCK_ACQUISITION;

    void stage(ForgotPasswordOtpResendIncident stage) {
      this.stage = Objects.requireNonNull(stage);
    }

    void recordFailure(ResendForgotPasswordOtpCommand command, Throwable cause) {
      recordFailure(command, cause, stage);
    }

    void recordFailure(
        ResendForgotPasswordOtpCommand command,
        Throwable cause,
        ForgotPasswordOtpResendIncident failureStage) {
      if (observedFailures.add(cause)) {
        incidents.add(failureStage);
        LOGGER.warn(
            "event=forgot-password.resend.failed requestId={} stage={} exceptionType={}",
            RequestIdLogSanitizer.sanitize(command.requestId()),
            failureStage.stage(),
            cause.getClass().getSimpleName());
      }
    }

    void record(ForgotPasswordOtpResendIncident incident) {
      incidents.add(incident);
    }

    void recordMalformedCounter(ResendForgotPasswordOtpCommand command) {
      record(ForgotPasswordOtpResendIncident.MALFORMED_RESEND_COUNT);
      LOGGER.warn(
          "event=forgot-password.resend.failed requestId={} stage={} exceptionType={}",
          RequestIdLogSanitizer.sanitize(command.requestId()),
          ForgotPasswordOtpResendIncident.MALFORMED_RESEND_COUNT.stage(),
          NumberFormatException.class.getSimpleName());
    }

    List<ForgotPasswordOtpResendIncident> snapshot() {
      return List.copyOf(incidents);
    }
  }

  private static final class DeliveryOutcomeUnknownException extends RuntimeException {
    private DeliveryOutcomeUnknownException() {
      super(null, null, false, false);
    }
  }
}
