package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class ForgotPasswordUnderLockDetailsVerifier {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ForgotPasswordUnderLockDetailsVerifier.class);

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
    return lookup
        .verify(
            new ForgotPasswordDetailsLookupCommand(
                context.identityType(),
                context.identityValue(),
                dob,
                email,
                mobile,
                context.username(),
                requestId,
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
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
        .doOnError(cause -> logFailure(requestId, cause))
        .onErrorReturn(
            ForgotPasswordUnderLockDetailsVerification.of(
                ForgotPasswordDetailsStatus.SYSTEM_FAILED));
  }

  private static void logFailure(String requestId, Throwable cause) {
    LOGGER.error(
        "event=forgot-password.details-otp.failed requestId={} stage=lookup exceptionType={}",
        requestId,
        cause.getClass().getSimpleName());
  }
}
