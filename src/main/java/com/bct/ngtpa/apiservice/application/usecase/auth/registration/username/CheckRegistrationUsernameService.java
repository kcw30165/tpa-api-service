package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import com.bct.ngtpa.apiservice.application.dto.auth.UsernamePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassification;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class CheckRegistrationUsernameService implements CheckRegistrationUsernameUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private static final String TARGET_PAGE = "PAGE";
  private static final String PAGE_ID = "registration-username-check";
  private static final String DOWNSTREAM_SOURCE = "APIM_TAS";
  private static final String DOWNSTREAM_ENDPOINT = "TRPChkCustId";

  private static final String ERROR_AUTH_FLOW_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID
          .value();
  private static final String ERROR_CSRF_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.CSRF_INVALID.value();
  private static final String ERROR_STATE_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.STATE_INVALID.value();
  private static final String ERROR_SELECTED_TRUST_REQUIRED =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.TRUST_SELECTION_INVALID
          .value();
  private static final String ERROR_SELECTED_TRUST_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.TRUST_SELECTION_INVALID
          .value();
  private static final String ERROR_USERNAME_INCORRECT =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.USERNAME_POLICY_INVALID
          .value();
  private static final String ERROR_DOWNSTREAM_UNAVAILABLE =
      com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey
          .DOWNSTREAM_UNAVAILABLE
          .value();
  private static final String ERROR_SYSTEM_UNAVAILABLE =
      com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE
          .value();

  private static final Set<AuthFlowStatus> ALLOWED_STATES =
      Set.of(
          AuthFlowStatus.OTP_VERIFIED,
          AuthFlowStatus.TRUST_SELECTION_READY,
          AuthFlowStatus.USERNAME_VERIFIED);

  private final AuthFlowSessionPort authFlowSessionPort;
  private final AuthFlowSessionExpiryRefreshPort expiryRefreshPort;
  private final RegistrationUsernameAvailabilityPort usernameAvailabilityPort;
  private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
  private final DownstreamErrorClassificationPort downstreamErrorClassificationPort;
  private final RegistrationUsernameCheckProtectorPort usernameCheckProtectorPort;
  private final Clock clock;

  public CheckRegistrationUsernameService(
      AuthFlowSessionPort authFlowSessionPort,
      AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
      RegistrationUsernameAvailabilityPort usernameAvailabilityPort,
      AuthWorkflowPolicyPort authWorkflowPolicyPort,
      DownstreamErrorClassificationPort downstreamErrorClassificationPort,
      RegistrationUsernameCheckProtectorPort usernameCheckProtectorPort,
      Clock clock) {
    this.authFlowSessionPort =
        Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
    this.expiryRefreshPort =
        Objects.requireNonNull(expiryRefreshPort, "expiryRefreshPort must not be null");
    this.usernameAvailabilityPort =
        Objects.requireNonNull(
            usernameAvailabilityPort, "usernameAvailabilityPort must not be null");
    this.authWorkflowPolicyPort =
        Objects.requireNonNull(authWorkflowPolicyPort, "authWorkflowPolicyPort must not be null");
    this.downstreamErrorClassificationPort =
        Objects.requireNonNull(
            downstreamErrorClassificationPort,
            "downstreamErrorClassificationPort must not be null");
    this.usernameCheckProtectorPort =
        Objects.requireNonNull(
            usernameCheckProtectorPort, "usernameCheckProtectorPort must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Mono<CheckRegistrationUsernameResult> check(CheckRegistrationUsernameCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return authFlowSessionPort
        .load(command.authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(session -> validateSessionAndProcess(command, session))
                    .orElseGet(
                        () ->
                            Mono.just(
                                failure(
                                    CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW,
                                    WorkflowNextStep.NONE,
                                    WorkflowErrorType.BUSINESS,
                                    ERROR_AUTH_FLOW_INVALID))));
  }

  private Mono<CheckRegistrationUsernameResult> validateSessionAndProcess(
      CheckRegistrationUsernameCommand command, AuthFlowSession session) {
    Instant now = Instant.now(clock);
    if (session.isExpired(now)
        || session.isTerminal()
        || session.scenario() != AuthFlowScenario.REGISTRATION) {
      return Mono.just(
          failure(
              CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW,
              WorkflowNextStep.NONE,
              WorkflowErrorType.BUSINESS,
              ERROR_AUTH_FLOW_INVALID));
    }
    if (!csrfMatches(command.csrfToken(), session.csrfTokenHash())) {
      return Mono.just(
          failure(
              CheckRegistrationUsernameResultStatus.CSRF_INVALID,
              WorkflowNextStep.NONE,
              WorkflowErrorType.BUSINESS,
              ERROR_CSRF_INVALID));
    }
    if (!ALLOWED_STATES.contains(session.status())) {
      return Mono.just(
          failure(
              CheckRegistrationUsernameResultStatus.INVALID_STATE,
              WorkflowNextStep.USERNAME_CHECK,
              WorkflowErrorType.FORM,
              ERROR_STATE_INVALID));
    }

    return expiryRefreshPort
        .refresh(command.authFlowSessionId(), now)
        .flatMap(refreshedSession -> processValidated(command, refreshedSession));
  }

  private Mono<CheckRegistrationUsernameResult> processValidated(
      CheckRegistrationUsernameCommand command, AuthFlowSession session) {
    NormalizedInput normalized = normalize(command);
    CheckRegistrationUsernameResult trustFailure = validateTrust(session, normalized);
    if (trustFailure != null) {
      return Mono.just(trustFailure);
    }
    if (!validUsername(normalized.username())) {
      return Mono.just(validationFailure(ERROR_USERNAME_INCORRECT));
    }
    if (isSameUsernameCheckRepeat(session, normalized)) {
      return Mono.just(success());
    }
    return checkAvailability(command, session, normalized);
  }

  private CheckRegistrationUsernameResult validateTrust(
      AuthFlowSession session, NormalizedInput normalized) {
    if (normalized.selectedTrustMissing()) {
      return validationFailure(ERROR_SELECTED_TRUST_REQUIRED);
    }
    if (normalized.selectedTrustInvalid()) {
      return validationFailure(ERROR_SELECTED_TRUST_INVALID);
    }
    if (!RegistrationEligibleTrustSet.contains(
        session.data() == null || session.data().attributes() == null
            ? null
            : session.data().attributes().get(RegistrationEligibleTrustSet.ATTRIBUTE_NAME),
        new RegistrationSelectedTrust(normalized.trustCode(), normalized.accountEnv()))) {
      return validationFailure(ERROR_SELECTED_TRUST_INVALID);
    }
    return null;
  }

  private Mono<CheckRegistrationUsernameResult> checkAvailability(
      CheckRegistrationUsernameCommand command,
      AuthFlowSession session,
      NormalizedInput normalized) {
    var availabilityCommand =
        new RegistrationUsernameAvailabilityCommand(
            new RegistrationSelectedTrust(normalized.trustCode(), normalized.accountEnv()),
            normalized.username(),
            command.correlationId(),
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    return usernameAvailabilityPort
        .checkAvailability(availabilityCommand)
        .flatMap(
            availability ->
                switch (availability.status()) {
                  case AVAILABLE -> storeUsernameCheck(command, session, normalized);
                  case REJECTED ->
                      Mono.just(downstreamRejected(availability.downstreamErrorCode()));
                  case DOWNSTREAM_FAILED -> Mono.just(downstreamFailed());
                })
        .onErrorResume(
            exception -> {
              log.error(
                  "event=registration.usernameCheck.failure severity=FATAL status=TECHNICAL_FAILURE correlationId={}",
                  safe(command.correlationId()));
              return Mono.just(downstreamFailed());
            });
  }

  private Mono<CheckRegistrationUsernameResult> storeUsernameCheck(
      CheckRegistrationUsernameCommand command, AuthFlowSession session, NormalizedInput input) {
    Instant now = Instant.now(clock);
    var usernameCheck =
        new UsernameCheckState(
            new SelectedTrust(input.trustCode(), input.accountEnv()), input.username(), now, null);
    AuthFlowSessionData currentData =
        session.data() == null ? AuthFlowSessionData.empty() : session.data();
    RegistrationUsernameCheckEncryptedState encrypted =
        usernameCheckProtectorPort.protect(usernameCheck);
    Map<String, String> attributes = new LinkedHashMap<>(currentData.attributes());
    attributes.put(
        RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT, encrypted.ciphertext());
    attributes.put(
        RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION, encrypted.keyVersion());
    attributes.put(
        RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM, encrypted.algorithm());
    var updatedData =
        new AuthFlowSessionData(
            currentData.contactType(), currentData.maskedDestination(), attributes);
    var updated =
        new AuthFlowSession(
            session.authFlowSessionId(),
            session.scenario(),
            AuthFlowStatus.USERNAME_VERIFIED,
            session.csrfTokenHash(),
            session.activeOtpChallengeId(),
            updatedData,
            session.createdAt(),
            now,
            session.expiresAt());
    return new com.bct.ngtpa.apiservice.application.usecase.session.authflow
            .RevisionAwareAuthFlowSessionWriter(authFlowSessionPort)
        .updateExpected(session, updated)
        .thenReturn(success());
  }

  private CheckRegistrationUsernameResult downstreamRejected(String downstreamErrorCode) {
    DownstreamErrorClassification classification =
        downstreamErrorClassificationPort.classify(
            new DownstreamErrorClassificationRequest(
                PAGE_ID,
                DOWNSTREAM_SOURCE,
                DOWNSTREAM_ENDPOINT,
                downstreamErrorCode == null || downstreamErrorCode.isBlank()
                    ? "unknown"
                    : downstreamErrorCode.trim()));
    String publicCode =
        publicUsernameRejectionCode(downstreamErrorCode, classification.responseCode());
    return failure(
        CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
        WorkflowNextStep.USERNAME_CHECK,
        WorkflowErrorType.BUSINESS,
        publicCode,
        "username");
  }

  private String publicUsernameRejectionCode(String downstreamErrorCode, String classifiedCode) {
    String normalized = downstreamErrorCode == null ? "" : downstreamErrorCode.trim();
    if (normalized.equals("err.tpa.username.taken")
        || normalized.equals("err.tpa.cust.id.duplicated")
        || normalized.equals("err.tpa.member.registered")) {
      return "err.registration.username.isRegistered";
    }
    if (classifiedCode != null && classifiedCode.startsWith("err.registration.username.")) {
      return classifiedCode;
    }
    return "err.registration.username.checkFailed";
  }

  private CheckRegistrationUsernameResult downstreamFailed() {
    log.error("event=registration.usernameCheck.failure severity=FATAL status=DOWNSTREAM_FAILURE");
    return failure(
        CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED,
        WorkflowNextStep.USERNAME_CHECK,
        WorkflowErrorType.DOWNSTREAM_SYSTEM,
        ERROR_DOWNSTREAM_UNAVAILABLE);
  }

  private CheckRegistrationUsernameResult validationFailure(String code) {
    return failure(
        CheckRegistrationUsernameResultStatus.VALIDATION_FAILED,
        WorkflowNextStep.USERNAME_CHECK,
        WorkflowErrorType.FORM,
        code);
  }

  private CheckRegistrationUsernameResult success() {
    return new CheckRegistrationUsernameResult(
        CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE,
        WorkflowNextStep.PASSWORD_SETUP,
        List.of(),
        List.of());
  }

  private CheckRegistrationUsernameResult failure(
      CheckRegistrationUsernameResultStatus status,
      WorkflowNextStep nextStep,
      WorkflowErrorType type,
      String code) {
    return failure(status, nextStep, type, code, TARGET_PAGE);
  }

  private CheckRegistrationUsernameResult failure(
      CheckRegistrationUsernameResultStatus status,
      WorkflowNextStep nextStep,
      WorkflowErrorType type,
      String code,
      String target) {
    return new CheckRegistrationUsernameResult(
        status,
        nextStep,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                type,
                code == null || code.isBlank() ? ERROR_SYSTEM_UNAVAILABLE : code,
                null,
                target == null || target.isBlank() ? List.of(TARGET_PAGE) : List.of(target),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)));
  }

  private NormalizedInput normalize(CheckRegistrationUsernameCommand command) {
    RegistrationSelectedTrust selectedTrust = command.selectedTrust();
    if (selectedTrust == null) {
      return new NormalizedInput(null, null, trim(command.username()), true);
    }
    return new NormalizedInput(
        trim(selectedTrust.trustCode()),
        trim(selectedTrust.accountEnv()),
        trim(command.username()),
        false);
  }

  private boolean validUsername(String username) {
    UsernamePolicy policy = authWorkflowPolicyPort.registrationPolicy().username();
    if (username == null || username.isBlank()) {
      return false;
    }
    if (policy != null) {
      if (policy.minLength() > 0 && username.length() < policy.minLength()) {
        return false;
      }
      if (policy.maxLength() > 0 && username.length() > policy.maxLength()) {
        return false;
      }
      if (policy.noSpaces() && username.chars().anyMatch(Character::isWhitespace)) {
        return false;
      }
      if (policy.requiredDigit() && username.chars().noneMatch(Character::isDigit)) {
        return false;
      }
      if (policy.requiredSpecial()
          && !containsAllowedSpecial(username, policy.allowedSpecialChars())) {
        return false;
      }
      if (!allowedCharacters(username, policy.allowedSpecialChars())) {
        return false;
      }
    }
    return true;
  }

  private boolean containsAllowedSpecial(String username, String allowedSpecialChars) {
    String allowed = allowedSpecialChars == null ? "" : allowedSpecialChars;
    return username.chars().anyMatch(ch -> allowed.indexOf(ch) >= 0);
  }

  private boolean allowedCharacters(String username, String allowedSpecialChars) {
    String allowed = allowedSpecialChars == null ? "" : allowedSpecialChars;
    return username
        .chars()
        .allMatch(ch -> Character.isLetterOrDigit(ch) || allowed.indexOf(ch) >= 0);
  }

  private boolean isSameUsernameCheckRepeat(AuthFlowSession session, NormalizedInput input) {
    if (session.status() != AuthFlowStatus.USERNAME_VERIFIED) {
      return false;
    }
    UsernameCheckState existing = existingUsernameCheck(session);
    if (existing == null || existing.selectedTrust() == null) {
      return false;
    }
    return input.trustCode().equals(existing.selectedTrust().trustCode())
        && input.accountEnv().equals(existing.selectedTrust().accountEnv())
        && input.username().equals(existing.username());
  }

  private UsernameCheckState existingUsernameCheck(AuthFlowSession session) {
    if (session.data() == null) {
      return null;
    }
    Map<String, String> attributes = session.data().attributes();
    String ciphertext =
        attributes.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT);
    String keyVersion =
        attributes.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION);
    String algorithm =
        attributes.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM);
    if (ciphertext == null || ciphertext.isBlank() || keyVersion == null || keyVersion.isBlank()) {
      return null;
    }
    try {
      return usernameCheckProtectorPort.unprotect(
          new RegistrationUsernameCheckEncryptedState(ciphertext, keyVersion, algorithm));
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private boolean csrfMatches(String rawToken, String expectedHash) {
    String token = trim(rawToken);
    String hash = trim(expectedHash);
    if (token == null || hash == null) {
      return false;
    }
    return sha256(token).equalsIgnoreCase(hash);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("CSRF hash unavailable");
    }
  }

  private String trim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private record NormalizedInput(
      String trustCode, String accountEnv, String username, boolean selectedTrustMissing) {
    public boolean selectedTrustInvalid() {
      return trustCode == null || accountEnv == null;
    }
  }
}
