package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
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

  public Mono<ForgotPasswordDetailsResult> verify(
      SessionLockHandle lock,
      AuthFlowSession session,
      ForgotPasswordAuthFlowContext context,
      String dob,
      String email,
      String mobile,
      String requestId) {
    if (lock == null || !lock.acquired() || session == null || context == null)
      return Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
    return lookup
        .verify(
            new ForgotPasswordDetailsLookupCommand(
                context.publicIdentityType(),
                context.tasUserId(),
                dob,
                email,
                mobile,
                context.customerId(),
                requestId,
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .map(
            r ->
                r.status() == ForgotPasswordDetailsLookupStatus.MATCHED
                    ? ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.DETAILS_VERIFIED)
                    : ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.BUSINESS_REJECTED))
        .onErrorReturn(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
  }
}
