package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ForgotPasswordUnderLockDetailsVerifier {
  private final ForgotPasswordDetailsLookupPort lookup;

  public ForgotPasswordUnderLockDetailsVerifier(ForgotPasswordDetailsLookupPort l) {
    lookup = Objects.requireNonNull(l);
  }

  public Mono<ForgotPasswordUnderLockDetailsVerification> verify(
      SessionLockHandle lock,
      AuthFlowSession session,
      ForgotPasswordAuthFlowContext context,
      String dob,
      String email,
      String mobile,
      String requestId) {
    if (lock == null || !lock.acquired() || session == null || context == null)
      return Mono.just(
          ForgotPasswordUnderLockDetailsVerification.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
    return Mono.defer(
            () ->
                lookup.verify(
            new ForgotPasswordDetailsLookupCommand(
                context.identityType(),
                context.identityValue(),
                dob,
                email,
                mobile,
                context.username(),
                requestId,
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .map(
            r ->
                r.status() == ForgotPasswordDetailsLookupStatus.MATCHED
                    ? new ForgotPasswordUnderLockDetailsVerification(
                        ForgotPasswordDetailsStatus.DETAILS_VERIFIED,
                        r.trustCode(),
                        r.schemeName(),
                        r.schemeNameChi())
                    : ForgotPasswordUnderLockDetailsVerification.of(
                        ForgotPasswordDetailsStatus.BUSINESS_REJECTED))
        .onErrorResume(
            cause -> {
              FailureOrigin origin = lookup.classifyFailure(cause);
              if (origin == null) {
                origin = FailureOrigin.LOCAL_SYSTEM;
              }
              if (isExpectedBusinessOrigin(origin)) {
                return Mono.just(
                    ForgotPasswordUnderLockDetailsVerification.of(
                        ForgotPasswordDetailsStatus.BUSINESS_REJECTED));
              }
              FailureOrigin terminalOrigin = terminalOrigin(origin);
              return Mono.just(
                  ForgotPasswordUnderLockDetailsVerification.technical(
                      lookup.isUnavailableFailure(cause)
                          ? ForgotPasswordDetailsStatus.SYSTEM_UNAVAILABLE
                          : ForgotPasswordDetailsStatus.SYSTEM_FAILED,
                      terminalOrigin));
            });
  }

  private static boolean isExpectedBusinessOrigin(FailureOrigin origin) {
    return origin == FailureOrigin.DOWNSTREAM_BUSINESS
        || origin == FailureOrigin.LOCAL_BUSINESS
        || origin == FailureOrigin.LOCAL_VALIDATION;
  }

  private static FailureOrigin terminalOrigin(FailureOrigin origin) {
    return switch (origin) {
      case DOWNSTREAM_CONTRACT, DOWNSTREAM_INVALID_RESPONSE, DOWNSTREAM_TRANSPORT, LOCAL_SYSTEM ->
          origin;
      case DOWNSTREAM_TECHNICAL -> FailureOrigin.LOCAL_SYSTEM;
      case DOWNSTREAM_BUSINESS, LOCAL_BUSINESS, LOCAL_VALIDATION ->
          throw new IllegalArgumentException("Expected business origin cannot own an incident");
    };
  }
}
