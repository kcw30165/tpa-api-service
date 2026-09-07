package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class VerifyRegistrationOtpService implements VerifyRegistrationOtpUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private final RegistrationProtectedAuthFlowContextLoader protectedContextService;
  private final RegistrationRequestHashGenerator requestHashGenerator;
  private final RegistrationIdempotencyPort idempotencyPort;
  private final RegistrationOtpPort otpGateway;
  private final RegistrationSessionPort sessionGateway;
  private final RegistrationLookupPort registrationLookupPort;
  private final RegistrationEligibleTrustPersistencePort eligibleTrustPersistencePort;
  private final Clock clock;

  public VerifyRegistrationOtpService(
      RegistrationProtectedAuthFlowContextLoader protectedContextLoader,
      RegistrationRequestHashGenerator requestHashGenerator,
      RegistrationIdempotencyPort idempotencyPort,
      RegistrationOtpPort otpGateway,
      RegistrationSessionPort sessionGateway,
      RegistrationLookupPort registrationLookupPort,
      Clock clock) {
    this(
        protectedContextLoader,
        requestHashGenerator,
        idempotencyPort,
        otpGateway,
        sessionGateway,
        registrationLookupPort,
        (sessionId, accounts, now) -> Mono.empty(),
        clock);
  }

  public VerifyRegistrationOtpService(
      RegistrationProtectedAuthFlowContextLoader protectedContextService,
      RegistrationRequestHashGenerator requestHashGenerator,
      RegistrationIdempotencyPort idempotencyPort,
      RegistrationOtpPort otpGateway,
      RegistrationSessionPort sessionGateway,
      RegistrationLookupPort registrationLookupPort,
      RegistrationEligibleTrustPersistencePort eligibleTrustPersistencePort,
      Clock clock) {
    this.protectedContextService = Objects.requireNonNull(protectedContextService);
    this.requestHashGenerator = Objects.requireNonNull(requestHashGenerator);
    this.idempotencyPort = Objects.requireNonNull(idempotencyPort);
    this.otpGateway = Objects.requireNonNull(otpGateway);
    this.sessionGateway = Objects.requireNonNull(sessionGateway);
    this.registrationLookupPort = Objects.requireNonNull(registrationLookupPort);
    this.eligibleTrustPersistencePort = Objects.requireNonNull(eligibleTrustPersistencePort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!validOtp(command.otpCode())) {
      return Mono.just(
          failure(
              VerifyRegistrationOtpResultStatus.VALIDATION_FAILED,
              WorkflowNextStep.VERIFY_OTP,
              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
              null,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    return protectedContextService
        .load(
            new RegistrationProtectedAuthFlowContextCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                command.origin(),
                command.referer(),
                command.correlationId()))
        .flatMap(
            context -> {
              if (context.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID) {
                return Mono.just(
                    failure(
                        VerifyRegistrationOtpResultStatus.CSRF_INVALID,
                        WorkflowNextStep.NONE,
                        RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID,
                        null,
                        RegistrationIdempotencyStartOutcome.STARTED));
              }
              if (!context.isValid()) {
                return Mono.just(
                    failure(
                        VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
                        WorkflowNextStep.NONE,
                        RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID,
                        null,
                        RegistrationIdempotencyStartOutcome.STARTED));
              }
              return processIdempotent(command, context);
            });
  }

  private Mono<VerifyRegistrationOtpResult> processIdempotent(
      VerifyRegistrationOtpCommand command, RegistrationProtectedAuthFlowContextResult context) {
    var hash =
        requestHashGenerator.hash(
            RegistrationIdempotencyAction.OTP_VERIFY,
            List.of(command.authFlowSessionId().value(), sha256(command.otpCode())));
    var startCommand =
        new RegistrationIdempotencyStartCommand(
            RegistrationIdempotencyAction.OTP_VERIFY,
            command.idempotencyKey(),
            hash,
            clock.instant(),
            command.idempotencyTtl());
    return idempotencyPort
        .startProcessing(startCommand)
        .flatMap(
            start ->
                switch (start.outcome()) {
                  case STARTED -> performVerify(command, context, hash);
                  case DUPLICATE_IN_PROGRESS ->
                      Mono.just(
                          new VerifyRegistrationOtpResult(
                              VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
                              WorkflowNextStep.WAIT,
                              List.of(),
                              null,
                              List.of(),
                              List.of(),
                              start.outcome(),
                              null));
                  case COMPLETED_REPLAY ->
                      Mono.just(
                          new VerifyRegistrationOtpResult(
                              VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY,
                              WorkflowNextStep.WAIT,
                              List.of(),
                              null,
                              List.of(),
                              List.of(),
                              start.outcome(),
                              start.record() == null ? null : start.record().responseSnapshot()));
                  case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                      Mono.just(
                          failure(
                              VerifyRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED,
                              WorkflowNextStep.NONE,
                              RegistrationOtpVerifyErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED,
                              null,
                              start.outcome()));
                  case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING ->
                      Mono.just(
                          failure(
                              VerifyRegistrationOtpResultStatus.SYSTEM_FAILED,
                              WorkflowNextStep.NONE,
                              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
                              null,
                              start.outcome()));
                });
  }

  private Mono<VerifyRegistrationOtpResult> performVerify(
      VerifyRegistrationOtpCommand command,
      RegistrationProtectedAuthFlowContextResult protectedContext,
      RegistrationRequestHash hash) {
    return otpGateway
        .verifyOtp(protectedContext.activeOtpChallengeId(), command.otpCode())
        .flatMap(
            otp ->
                switch (otp.status()) {
                  case VERIFIED ->
                      sessionGateway
                          .markOtpVerified(command.authFlowSessionId().value())
                          .then(lookupTrusts(command, protectedContext, hash));
                  case CREATED ->
                      markFailed(command, hash)
                          .then(
                              Mono.just(
                                  failure(
                                      VerifyRegistrationOtpResultStatus.OTP_INVALID,
                                      WorkflowNextStep.VERIFY_OTP,
                                      RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
                                      otp.remainingVerifyAttempts(),
                                      RegistrationIdempotencyStartOutcome.STARTED)));
                  case CONSUMED ->
                      markFailed(command, hash)
                          .then(
                              Mono.just(
                                  failure(
                                      VerifyRegistrationOtpResultStatus.OTP_INVALID,
                                      WorkflowNextStep.VERIFY_OTP,
                                      RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
                                      otp.remainingVerifyAttempts(),
                                      RegistrationIdempotencyStartOutcome.STARTED)));
                  case INVALIDATED ->
                      markFailed(command, hash)
                          .then(
                              Mono.just(
                                  failure(
                                      VerifyRegistrationOtpResultStatus.OTP_INVALID,
                                      WorkflowNextStep.VERIFY_OTP,
                                      RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
                                      otp.remainingVerifyAttempts(),
                                      RegistrationIdempotencyStartOutcome.STARTED)));
                  case EXPIRED ->
                      markFailed(command, hash)
                          .then(
                              Mono.just(
                                  failure(
                                      VerifyRegistrationOtpResultStatus.OTP_EXPIRED,
                                      WorkflowNextStep.VERIFY_OTP,
                                      RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_EXPIRED,
                                      null,
                                      RegistrationIdempotencyStartOutcome.STARTED)));
                  case LOCKED ->
                      markFailed(command, hash)
                          .then(
                              Mono.just(
                                  failure(
                                      VerifyRegistrationOtpResultStatus.OTP_LOCKED,
                                      WorkflowNextStep.RESTART_FLOW,
                                      RegistrationOtpVerifyErrorKeys
                                          .REGISTRATION_OTP_MAX_ATTEMPTS_REACHED,
                                      0,
                                      RegistrationIdempotencyStartOutcome.STARTED)));
                });
  }

  private Mono<VerifyRegistrationOtpResult> lookupTrusts(
      VerifyRegistrationOtpCommand command,
      RegistrationProtectedAuthFlowContextResult protectedContext,
      RegistrationRequestHash hash) {
    RegistrationAuthFlowContext context = protectedContext.registrationContext();
    RegistrationLookupCommand lookupCommand =
        new RegistrationLookupCommand(
            context.identityType(),
            context.identityValue(),
            context.dateOfBirth(),
            context.contactType(),
            context.contactValue(),
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    return registrationLookupPort
        .searchMember(lookupCommand)
        .flatMap(
            result -> {
              String statusName = result.status() == null ? "" : result.status().name();
              if (!"ELIGIBLE".equals(statusName)) {
                return markFailed(command, hash)
                    .then(
                        Mono.just(
                            failure(
                                VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS,
                                WorkflowNextStep.VERIFY_OTP,
                                RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS,
                                null,
                                RegistrationIdempotencyStartOutcome.STARTED)));
              }
              List<RegistrationLookupAccount> unregisteredAccounts =
                  result.accounts().stream()
                      .filter(account -> Boolean.FALSE.equals(account.registered()))
                      .toList();
              List<RegistrationUnregisteredTrust> trusts =
                  unregisteredAccounts.stream()
                      .map(a -> new RegistrationUnregisteredTrust(a.accountEnv(), a.trustCode()))
                      .toList();
              if (trusts.isEmpty()) {
                return markFailed(command, hash)
                    .then(
                        Mono.just(
                            failure(
                                VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS,
                                WorkflowNextStep.VERIFY_OTP,
                                RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS,
                                null,
                                RegistrationIdempotencyStartOutcome.STARTED)));
              }
              WorkflowNextStep next =
                  trusts.size() == 1
                      ? WorkflowNextStep.USERNAME_CHECK
                      : WorkflowNextStep.SELECT_TRUST;
              var verifyResult =
                  new VerifyRegistrationOtpResult(
                      VerifyRegistrationOtpResultStatus.VERIFIED,
                      next,
                      trusts,
                      null,
                      List.of(),
                      List.of(),
                      RegistrationIdempotencyStartOutcome.STARTED,
                      null);
              return eligibleTrustPersistencePort
                  .store(command.authFlowSessionId(), unregisteredAccounts, clock.instant())
                  .then(markCompleted(command, hash, verifyResult))
                  .thenReturn(verifyResult);
            });
  }

  private Mono<RegistrationIdempotencyRecord> markCompleted(
      VerifyRegistrationOtpCommand command,
      RegistrationRequestHash hash,
      VerifyRegistrationOtpResult result) {
    return idempotencyPort.markCompleted(
        new RegistrationIdempotencyCompleteCommand(
            RegistrationIdempotencyAction.OTP_VERIFY,
            command.idempotencyKey(),
            hash,
            snapshot(result),
            clock.instant(),
            command.idempotencyTtl()));
  }

  private Mono<RegistrationIdempotencyRecord> markFailed(
      VerifyRegistrationOtpCommand command, RegistrationRequestHash hash) {
    return idempotencyPort.markFailed(
        new RegistrationIdempotencyFailCommand(
            RegistrationIdempotencyAction.OTP_VERIFY,
            command.idempotencyKey(),
            hash,
            RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
            clock.instant(),
            command.idempotencyTtl()));
  }

  private RegistrationIdempotencyResponseSnapshot snapshot(VerifyRegistrationOtpResult result) {
    String body =
        "{\"nextStep\":\""
            + result.nextStep().name()
            + "\",\"trustCount\":"
            + result.unregisteredTrusts().size()
            + "}";
    return new RegistrationIdempotencyResponseSnapshot("200", body, Map.of());
  }

  private VerifyRegistrationOtpResult failure(
      VerifyRegistrationOtpResultStatus status,
      WorkflowNextStep next,
      String code,
      Integer remaining,
      RegistrationIdempotencyStartOutcome outcome) {
    return new VerifyRegistrationOtpResult(
        status,
        next,
        List.of(),
        remaining,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.FORM,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)),
        outcome,
        null);
  }

  private boolean validOtp(String otp) {
    return otp != null && otp.matches("\\d{6}");
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("OTP hash unavailable");
    }
  }
}
