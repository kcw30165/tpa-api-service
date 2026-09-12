package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/** START business workflow without idempotency reservation or persistence responsibility. */
public final class ForgotPasswordStartProtectedOperation {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ForgotPasswordStartProtectedOperation.class);
  private static final String WORKFLOW_VERSION = "v1";
  private static final String ERROR_USERNAME_NOT_FOUND = "err.forgotpassword.username.notFound";
  private static final String ERROR_SYSTEM_UNAVAILABLE = "err.forgotpassword.system.unavailable";

  private final ForgotPasswordUsernameLookupPort lookupPort;
  private final ForgotPasswordAuthFlowContextProtectorPort contextProtector;
  private final AuthFlowSessionConcurrencyService concurrencyService;
  private final CreateAuthFlowSessionCsrfUseCase csrfUseCase;
  private final Clock clock;
  private final SessionTtlPolicy sessionTtlPolicy;
  private final SessionConcurrencyPolicy concurrencyPolicy;
  private final String sessionIdPrefix;
  private final int sessionIdEntropyBytes;
  private final boolean trimUsername;
  private final boolean caseInsensitiveUsername;
  private final boolean asciiLowercase;
  private final Map<String, MemberIdentityType> tasIdentityTypeMapping;

  public ForgotPasswordStartProtectedOperation(
      ForgotPasswordUsernameLookupPort lookupPort,
      ForgotPasswordAuthFlowContextProtectorPort contextProtector,
      AuthFlowSessionConcurrencyService concurrencyService,
      CreateAuthFlowSessionCsrfUseCase csrfUseCase,
      Clock clock,
      SessionTtlPolicy sessionTtlPolicy,
      SessionConcurrencyPolicy concurrencyPolicy,
      String sessionIdPrefix,
      int sessionIdEntropyBytes,
      boolean trimUsername,
      boolean caseInsensitiveUsername,
      boolean asciiLowercase,
      Map<String, MemberIdentityType> tasIdentityTypeMapping) {
    this.lookupPort = Objects.requireNonNull(lookupPort, "lookupPort must not be null");
    this.contextProtector =
        Objects.requireNonNull(contextProtector, "contextProtector must not be null");
    this.concurrencyService =
        Objects.requireNonNull(concurrencyService, "concurrencyService must not be null");
    this.csrfUseCase = Objects.requireNonNull(csrfUseCase, "csrfUseCase must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.sessionTtlPolicy =
        Objects.requireNonNull(sessionTtlPolicy, "sessionTtlPolicy must not be null");
    this.concurrencyPolicy =
        Objects.requireNonNull(concurrencyPolicy, "concurrencyPolicy must not be null");
    this.sessionIdPrefix =
        Objects.requireNonNull(sessionIdPrefix, "sessionIdPrefix must not be null");
    this.sessionIdEntropyBytes = sessionIdEntropyBytes;
    this.trimUsername = trimUsername;
    this.caseInsensitiveUsername = caseInsensitiveUsername;
    this.asciiLowercase = asciiLowercase;
    this.tasIdentityTypeMapping = Map.copyOf(Objects.requireNonNull(tasIdentityTypeMapping));
  }

  public Mono<StartForgotPasswordResult> execute(StartForgotPasswordCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String normalizedUsername = normalizeUsername(command.username());
    return lookupPort
        .lookup(
            new StartForgotPasswordUsernameLookupCommand(
                normalizedUsername, command.correlationId(), command.actorUserRole()))
        .flatMap(
            result ->
                switch (result.status()) {
                  case CONFIRMED -> createStartedResult(command, normalizedUsername, result);
                  case NOT_FOUND -> Mono.just(notFound());
                  case SYSTEM_FAILED -> Mono.just(systemFailed());
                })
        .onErrorResume(
            error -> {
              LOGGER.error(
                  "event=forgot-password.start.failed requestId={} stage=username-lookup exceptionType={}",
                  command.correlationId(),
                  error.getClass().getName(),
                  error);
              return Mono.just(systemFailed());
            });
  }

  public String normalizeUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("username must not be blank");
    }
    String normalized = trimUsername ? username.trim() : username;
    if (caseInsensitiveUsername || asciiLowercase) {
      normalized = normalized.toLowerCase(Locale.ROOT);
    }
    return normalized;
  }

  private Mono<StartForgotPasswordResult> createStartedResult(
      StartForgotPasswordCommand command,
      String normalizedUsername,
      ForgotPasswordUsernameLookupResult lookupResult) {
    String publicIdentityType = publicIdentityType(lookupResult.identityType());
    var protectedContext =
        contextProtector.protect(
            new ForgotPasswordAuthFlowContext(
                lookupResult.customerId(),
                lookupResult.userId(),
                publicIdentityType,
                false,
                null,
                null,
                null,
                null,
                null,
                WORKFLOW_VERSION));
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(
        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT,
        protectedContext.ciphertext());
    attributes.put(
        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION,
        protectedContext.keyVersion());
    attributes.put(
        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM,
        protectedContext.algorithm());
    var sessionCommand =
        new StartAuthFlowSessionWithConcurrencyCommand(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.STARTED,
            sessionTtlPolicy,
            sessionIdPrefix,
            sessionIdEntropyBytes,
            new AuthFlowSessionData(null, null, attributes),
            null,
            null,
            authFlowSessionId(command.existingAuthFlowSessionId()),
            concurrencyPolicy,
            command.correlationId());
    return concurrencyService
        .start(sessionCommand)
        .flatMap(
            started ->
                csrfUseCase
                    .createForSession(
                        new CreateAuthFlowSessionCsrfCommand(
                            started.session().authFlowSessionId(),
                            Instant.now(clock),
                            started.expiresInSeconds(),
                            command.correlationId()))
                    .map(
                        csrf ->
                            success(
                                publicIdentityType,
                                started.session().authFlowSessionId().value(),
                                csrf.rawToken())))
        .onErrorResume(
            error -> {
              LOGGER.error(
                  "event=forgot-password.start.failed requestId={} stage=session-replacement-or-csrf exceptionType={}",
                  command.correlationId(),
                  error.getClass().getName(),
                  error);
              return Mono.just(
                  isSessionReplacementConflict(error) ? authFlowInvalid() : systemFailed());
            });
  }

  private StartForgotPasswordResult success(
      String identityType, String authFlowSessionId, String csrfToken) {
    return new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.STARTED,
        WorkflowNextStep.ENTER_ID,
        csrfToken,
        identityType,
        authFlowSessionId,
        List.of(),
        null);
  }

  private StartForgotPasswordResult notFound() {
    return new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
        WorkflowNextStep.ENTER_USERNAME,
        null,
        null,
        null,
        List.of(new StartForgotPasswordError(ERROR_USERNAME_NOT_FOUND, "username", null)),
        null);
  }

  private StartForgotPasswordResult authFlowInvalid() {
    return new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.AUTH_FLOW_INVALID,
        WorkflowNextStep.RESTART_FLOW,
        null,
        null,
        null,
        List.of(
            new StartForgotPasswordError(
                ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value(), "PAGE", null)),
        null);
  }

  private StartForgotPasswordResult systemFailed() {
    return new StartForgotPasswordResult(
        StartForgotPasswordResultStatus.SYSTEM_FAILED,
        WorkflowNextStep.NONE,
        null,
        null,
        null,
        List.of(new StartForgotPasswordError(ERROR_SYSTEM_UNAVAILABLE, "PAGE", null)),
        null);
  }

  private boolean isSessionReplacementConflict(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof IllegalStateException
          && "Auth flow session revision conflict".equals(current.getMessage())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId
      authFlowSessionId(String value) {
    return value == null || value.isBlank()
        ? null
        : new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId(value);
  }

  private String publicIdentityType(MemberIdentityType identityType) {
    return identityType == MemberIdentityType.STAFFNO ? "STAFF_NO" : identityType.name();
  }
}
