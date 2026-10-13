package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Idempotency-neutral acquired execution for Username Recovery REQUEST. */
public final class UsernameRecoveryRequestProtectedOperation {
  private final UsernameRecoveryAuthFlowContextBinding contextBinding;
  private final CsrfTokenValidationService csrfTokenValidationService;
  private final UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort;
  private final AuthFlowSessionPort authFlowSessionPort;
  private final MessageDeliveryPort messageDeliveryPort;
  private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();
  private final List<String> allowedOrigins;
  private final MaskingService maskingService;
  private final NoticeTemplateRegistryPort noticeTemplateRegistryPort;

  public UsernameRecoveryRequestProtectedOperation(
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenValidationService csrfTokenValidationService,
      UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort,
      AuthFlowSessionPort authFlowSessionPort,
      MessageDeliveryPort messageDeliveryPort,
      MaskingService maskingService,
      List<String> allowedOrigins,
      NoticeTemplateRegistryPort noticeTemplateRegistryPort) {
    this.contextBinding = Objects.requireNonNull(contextBinding);
    this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService);
    this.effectiveDestinationPort = Objects.requireNonNull(effectiveDestinationPort);
    this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort);
    this.messageDeliveryPort = Objects.requireNonNull(messageDeliveryPort);
    this.maskingService = Objects.requireNonNull(maskingService);
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    this.noticeTemplateRegistryPort = Objects.requireNonNull(noticeTemplateRegistryPort);
  }

  public Mono<UsernameRecoveryRequestProtectedExecutionResult> execute(
      UsernameRecoveryRequestCommand command,
      String rawCsrfToken,
      String origin,
      String referer,
      String correlationId,
      Instant now) {
    if (command == null || now == null || !nonBlank(correlationId)) {
      return completed(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED);
    }
    if (!allowedOrigins.isEmpty()
        && !originRefererValidator.validate(origin, referer, allowedOrigins).valid()) {
      return Mono.error(UsernameRecoveryRequestException.securityInvalid());
    }
    return authFlowSessionPort
        .loadSnapshot(command.authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(
                        snapshot ->
                            reserveCandidate(snapshot, command, rawCsrfToken, correlationId, now))
                    .orElseGet(() -> Mono.error(UsernameRecoveryRequestException.sessionAbsent())))
        .onErrorResume(UsernameRecoveryRequestException.class, Mono::error)
        .onErrorResume(
            error ->
                Mono.just(
                    UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(error)));
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> reserveCandidate(
      AuthFlowSessionSnapshot snapshot,
      UsernameRecoveryRequestCommand command,
      String rawCsrfToken,
      String correlationId,
      Instant now) {
    AuthFlowSession session = snapshot.session();
    if (session.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
      return Mono.error(UsernameRecoveryRequestException.sessionAbsent());
    }
    if (session.isExpired(now)) {
      return Mono.error(UsernameRecoveryRequestException.sessionExpired());
    }
    if (session.isTerminal() || session.status() != AuthFlowStatus.STARTED) {
      return completed(UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED);
    }
    if (!csrfTokenValidationService.validate(rawCsrfToken, session.csrfTokenHash()).valid()) {
      return Mono.error(UsernameRecoveryRequestException.securityInvalid());
    }
    final UsernameRecoveryAuthFlowContext context;
    try {
      context = contextBinding.load(session);
    } catch (RuntimeException error) {
      return Mono.just(UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(error));
    }
    UsernameRecoverySessionCandidate candidate = findCandidate(context, command.schemeRef());
    if (candidate == null)
      return completed(UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF);
    if (!candidate.registered()) {
      return completed(UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE);
    }
    if (candidate.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED) {
      return completed(UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED);
    }
    if (candidate.deliveryState() != UsernameRecoveryDeliveryState.AVAILABLE) {
      return Mono.error(UsernameRecoveryRequestException.candidateNotAvailable());
    }
    UsernameRecoveryAuthFlowContext reserved = context.reserve(command.schemeRef());
    AuthFlowSession updated = contextBinding.bind(session, reserved, now);
    return authFlowSessionPort
        .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(snapshot, updated))
        .flatMap(
            result -> {
              if (result.outcome() == AuthFlowSessionMutationOutcome.APPLIED) {
                return deliver(result.snapshot(), reserved, command, correlationId, now);
              }
              if (result.outcome() == AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED
                  || result.outcome() == AuthFlowSessionMutationOutcome.EXPECTED_STATE_CHANGED) {
                return Mono.error(UsernameRecoveryRequestException.revisionConflict());
              }
              if (result.outcome() == AuthFlowSessionMutationOutcome.SESSION_NOT_FOUND) {
                return Mono.error(UsernameRecoveryRequestException.sessionAbsent());
              }
              return Mono.just(
                  UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(
                      new IllegalStateException("candidate reservation failed")));
            });
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> deliver(
      AuthFlowSessionSnapshot reservedSnapshot,
      UsernameRecoveryAuthFlowContext context,
      UsernameRecoveryRequestCommand command,
      String correlationId,
      Instant now) {
    UsernameRecoverySessionCandidate candidate = findCandidate(context, command.schemeRef());
    return effectiveDestinationPort
        .resolve(new UsernameRecoveryEffectiveDestinationCommand(context.verifiedDestination()))
        .materialize()
        .flatMap(
            resolution -> {
              if (!resolution.hasValue()) {
                return persistFailed(reservedSnapshot, context, command, now);
              }
              UsernameRecoveryEffectiveDestinationResult effective = resolution.get();
              String masked =
                  context.verifiedDestination().channel()
                          == UsernameRecoveryVerifiedDestination.Channel.EMAIL
                      ? maskingService.maskEmail(context.verifiedDestination().value())
                      : maskingService.maskMobile(context.verifiedDestination().value());
              MessageDeliveryCommand delivery =
                  buildDeliveryCommand(context, candidate, effective, correlationId, command);
              return messageDeliveryPort
                  .deliver(delivery)
                  .switchIfEmpty(
                      Mono.error(
                          new UsernameRecoveryRequestDispatchException(
                              "delivery publisher completed empty")))
                  .flatMap(
                      result ->
                          classifyDelivery(
                              reservedSnapshot, context, command, effective, masked, result, now))
                  .onErrorResume(
                      error ->
                          persistUnknown(reservedSnapshot, context, command, now)
                              .thenReturn(
                                  UsernameRecoveryRequestProtectedExecutionResult
                                      .possibleSideEffect(error)));
            });
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> classifyDelivery(
      AuthFlowSessionSnapshot reservedSnapshot,
      UsernameRecoveryAuthFlowContext context,
      UsernameRecoveryRequestCommand command,
      UsernameRecoveryEffectiveDestinationResult effective,
      String maskedDestination,
      MessageDeliveryResult result,
      Instant now) {
    if (result == null
        || result.status() == null
        || result.status() == MessageDeliveryStatus.UNKNOWN_OUTCOME) {
      return persistUnknown(reservedSnapshot, context, command, now)
          .thenReturn(
              UsernameRecoveryRequestProtectedExecutionResult.possibleSideEffect(
                  new IllegalStateException("delivery outcome is unknown")));
    }
    if (result.status() == MessageDeliveryStatus.FAILED) {
      return persistFailed(reservedSnapshot, context, command, now);
    }
    if (result.status() == MessageDeliveryStatus.ACCEPTED) {
      String senderReference =
          nonBlank(result.providerMessageId()) ? result.providerMessageId() : "provider-accepted";
      UsernameRecoveryAuthFlowContext requested =
          context.markRequested(command.schemeRef(), senderReference, now);
      AuthFlowStatus status =
          requested.workflowStatus() == UsernameRecoveryWorkflowStatus.COMPLETED
              ? AuthFlowStatus.COMPLETED
              : AuthFlowStatus.STARTED;
      UsernameRecoveryRequestResult accepted =
          new UsernameRecoveryRequestResult(
              UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
              requested.workflowStatus(),
              command.schemeRef(),
              maskedDestination,
              destinationType(effective.channel()));
      return persist(reservedSnapshot, requested, status, now)
          .thenReturn(UsernameRecoveryRequestProtectedExecutionResult.completed(accepted))
          .onErrorReturn(
              UsernameRecoveryRequestProtectedExecutionResult.possibleSideEffect(
                  new IllegalStateException("accepted delivery persistence failed")));
    }
    return persistUnknown(reservedSnapshot, context, command, now)
        .thenReturn(
            UsernameRecoveryRequestProtectedExecutionResult.possibleSideEffect(
                new IllegalStateException("unsupported delivery status")));
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> persistFailed(
      AuthFlowSessionSnapshot snapshot,
      UsernameRecoveryAuthFlowContext context,
      UsernameRecoveryRequestCommand command,
      Instant now) {
    UsernameRecoveryAuthFlowContext failed = context.markFailed(command.schemeRef());
    return persist(snapshot, failed, AuthFlowStatus.STARTED, now)
        .then(completed(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
        .onErrorReturn(
            UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(
                new IllegalStateException("pre-dispatch failure persistence failed")));
  }

  private Mono<AuthFlowSessionSnapshot> persistUnknown(
      AuthFlowSessionSnapshot snapshot,
      UsernameRecoveryAuthFlowContext context,
      UsernameRecoveryRequestCommand command,
      Instant now) {
    return persist(
        snapshot,
        context.markUnknownOutcome(command.schemeRef()),
        AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED,
        now);
  }

  private Mono<AuthFlowSessionSnapshot> persist(
      AuthFlowSessionSnapshot expected,
      UsernameRecoveryAuthFlowContext context,
      AuthFlowStatus status,
      Instant now) {
    AuthFlowSession current = expected.session();
    AuthFlowSession base =
        new AuthFlowSession(
            current.authFlowSessionId(),
            current.scenario(),
            status,
            current.csrfTokenHash(),
            current.activeOtpChallengeId(),
            current.data(),
            current.createdAt(),
            now,
            current.expiresAt());
    AuthFlowSession updated = contextBinding.bind(base, context, now);
    return authFlowSessionPort
        .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(expected, updated))
        .flatMap(
            result ->
                result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                    ? Mono.just(result.snapshot())
                    : Mono.error(new IllegalStateException("post-delivery envelope CAS failed")));
  }

  private MessageDeliveryCommand buildDeliveryCommand(
      UsernameRecoveryAuthFlowContext context,
      UsernameRecoverySessionCandidate candidate,
      UsernameRecoveryEffectiveDestinationResult effective,
      String correlationId,
      UsernameRecoveryRequestCommand requestCommand) {
    MessageDeliveryChannel channel =
        effective.channel() == UsernameRecoveryVerifiedDestination.Channel.EMAIL
            ? MessageDeliveryChannel.EMAIL
            : MessageDeliveryChannel.SMS;
    String locale = requestCommand.languageOr(context.language()).toLanguageTag();
    NoticeTemplateDefinition definition =
        noticeTemplateRegistryPort.resolve(MessageDeliveryType.USERNAME_RECOVERY, channel, locale);
    Map<String, String> variables = new LinkedHashMap<>();
    putIfPresent(variables, "username", candidate.userName());
    putIfPresent(variables, "trustCode", candidate.trustCode());
    putIfPresent(variables, "schemeName", candidate.schemeName());
    putIfPresent(variables, "schemeNameChi", resolveSchemeNameChi(candidate));
    return new MessageDeliveryCommand(
        MessageDeliveryType.USERNAME_RECOVERY,
        MessageDeliveryScenario.USERNAME_RECOVERY,
        channel,
        effective.destination(),
        definition.subject(),
        definition.body(),
        null,
        variables,
        locale,
        correlationId);
  }

  private static String resolveSchemeNameChi(UsernameRecoverySessionCandidate candidate) {
    String schemeNameChi = candidate.schemeNameChi();
    return (schemeNameChi == null || schemeNameChi.isBlank())
        ? candidate.schemeName()
        : schemeNameChi;
  }

  private static void putIfPresent(Map<String, String> variables, String key, String value) {
    if (value != null) {
      variables.put(key, value);
    }
  }

  private static Mono<UsernameRecoveryRequestProtectedExecutionResult> completed(
      UsernameRecoveryRequestResult.Outcome outcome) {
    return Mono.just(
        UsernameRecoveryRequestProtectedExecutionResult.completed(
            new UsernameRecoveryRequestResult(outcome, null, null, null, null)));
  }

  private static UsernameRecoverySessionCandidate findCandidate(
      UsernameRecoveryAuthFlowContext context, UsernameRecoverySchemeRef schemeRef) {
    return context.candidates().stream()
        .filter(candidate -> candidate.schemeRef().equals(schemeRef))
        .findFirst()
        .orElse(null);
  }

  private static UsernameRecoveryDestinationType destinationType(
      UsernameRecoveryVerifiedDestination.Channel channel) {
    return channel == UsernameRecoveryVerifiedDestination.Channel.EMAIL
        ? UsernameRecoveryDestinationType.EMAIL
        : UsernameRecoveryDestinationType.MOBILE;
  }

  private static final class UsernameRecoveryRequestDispatchException extends RuntimeException {
    private UsernameRecoveryRequestDispatchException(String message) {
      super(message);
    }
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }
}
