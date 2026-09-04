package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerifyErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Acquired OTP Verify business workflow with no idempotency persistence responsibility. */
public final class RegistrationOtpVerifyProtectedOperation {
  private static final String SOURCE_SERVER = "SERVER";

  private final RegistrationOtpPort otp;
  private final RegistrationSessionPort sessions;
  private final RegistrationLookupPort lookup;
  private final RegistrationEligibleTrustPersistencePort eligibleTrusts;
  private final Clock clock;

  public RegistrationOtpVerifyProtectedOperation(
      RegistrationOtpPort otp,
      RegistrationSessionPort sessions,
      RegistrationLookupPort lookup,
      RegistrationEligibleTrustPersistencePort eligibleTrusts,
      Clock clock) {
    this.otp = Objects.requireNonNull(otp);
    this.sessions = Objects.requireNonNull(sessions);
    this.lookup = Objects.requireNonNull(lookup);
    this.eligibleTrusts = Objects.requireNonNull(eligibleTrusts);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<VerifyRegistrationOtpResult> execute(
      VerifyRegistrationOtpCommand command,
      RegistrationProtectedAuthFlowContextResult protectedContext) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(protectedContext, "protectedContext must not be null");
    if (!protectedContext.isValid()) {
      return Mono.error(new IllegalArgumentException("protectedContext must be valid"));
    }
    return otp.verifyOtp(protectedContext.activeOtpChallengeId(), command.otpCode())
        .flatMap(
            result ->
                switch (result.status()) {
                  case VERIFIED ->
                      sessions
                          .markOtpVerified(command.authFlowSessionId().value())
                          .then(lookupTrusts(command, protectedContext));
                  case CREATED, CONSUMED, INVALIDATED ->
                      Mono.just(
                          failure(
                              VerifyRegistrationOtpResultStatus.OTP_INVALID,
                              WorkflowNextStep.VERIFY_OTP,
                              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
                              result.remainingVerifyAttempts()));
                  case EXPIRED ->
                      Mono.just(
                          failure(
                              VerifyRegistrationOtpResultStatus.OTP_EXPIRED,
                              WorkflowNextStep.VERIFY_OTP,
                              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_EXPIRED,
                              null));
                  case LOCKED ->
                      Mono.just(
                          failure(
                              VerifyRegistrationOtpResultStatus.OTP_LOCKED,
                              WorkflowNextStep.RESTART_FLOW,
                              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED,
                              0));
                });
  }

  private Mono<VerifyRegistrationOtpResult> lookupTrusts(
      VerifyRegistrationOtpCommand command,
      RegistrationProtectedAuthFlowContextResult protectedContext) {
    RegistrationAuthFlowContext context = protectedContext.registrationContext();
    var lookupCommand =
        new RegistrationLookupCommand(
            context.identityType(),
            context.identityValue(),
            context.dateOfBirth(),
            context.contactType(),
            context.contactValue(),
            ActorUserRole.ROLE_USER_MBR);
    return lookup
        .searchUnregisteredMember(lookupCommand)
        .flatMap(
            result -> {
              if (result.status() != RegistrationLookupStatus.ELIGIBLE) {
                return Mono.just(noEligibleTrusts());
              }
              List<RegistrationUnregisteredTrust> trusts =
                  result.accounts().stream()
                      .map(
                          account ->
                              new RegistrationUnregisteredTrust(
                                  account.accountEnv(), account.trustCode()))
                      .toList();
              if (trusts.isEmpty()) {
                return Mono.just(noEligibleTrusts());
              }
              WorkflowNextStep nextStep =
                  trusts.size() == 1
                      ? WorkflowNextStep.USERNAME_CHECK
                      : WorkflowNextStep.SELECT_TRUST;
              var verified =
                  new VerifyRegistrationOtpResult(
                      VerifyRegistrationOtpResultStatus.VERIFIED,
                      nextStep,
                      trusts,
                      null,
                      List.of(),
                      List.of(),
                      RegistrationIdempotencyStartOutcome.STARTED,
                      null);
              return eligibleTrusts
                  .store(command.authFlowSessionId(), result.accounts(), clock.instant())
                  .thenReturn(verified);
            });
  }

  private VerifyRegistrationOtpResult noEligibleTrusts() {
    return failure(
        VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS,
        WorkflowNextStep.VERIFY_OTP,
        RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS,
        null);
  }

  private VerifyRegistrationOtpResult failure(
      VerifyRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      String code,
      Integer remainingAttempts) {
    return new VerifyRegistrationOtpResult(
        status,
        nextStep,
        List.of(),
        remainingAttempts,
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
}
