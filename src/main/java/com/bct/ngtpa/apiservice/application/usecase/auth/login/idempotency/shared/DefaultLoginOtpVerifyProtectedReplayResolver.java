package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginByHandoffCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginByHandoffUseCase;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Resolves an opaque OTP Verify handoff through committed-login recovery only. */
public final class DefaultLoginOtpVerifyProtectedReplayResolver
    implements LoginOtpVerifyProtectedReplayResolver {
  private final RecoverCommittedLoginByHandoffUseCase committedLoginRecovery;

  public DefaultLoginOtpVerifyProtectedReplayResolver(
      RecoverCommittedLoginByHandoffUseCase committedLoginRecovery) {
    this.committedLoginRecovery = Objects.requireNonNull(committedLoginRecovery);
  }

  @Override
  public Mono<VerifyLoginOtpResult> resolve(
      ProtectedReplayReference protectedReplayReference, String correlationId) {
    if (protectedReplayReference == null) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify protected replay reference is required"));
    }
    AuthenticatedLoginHandoffReference handoffReference;
    try {
      handoffReference = new AuthenticatedLoginHandoffReference(protectedReplayReference.value());
    } catch (RuntimeException exception) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify protected replay reference is invalid"));
    }
    return committedLoginRecovery
        .recover(new RecoverCommittedLoginByHandoffCommand(handoffReference, correlationId))
        .filter(recovered -> recovered != null && recovered.recovered())
        .map(
            recovered ->
                new VerifyLoginOtpResult(
                    VerifyLoginOtpStatus.ACCOUNTS_READY,
                    null,
                    true,
                    recovered.accounts(),
                    recovered.authenticatedSessionId(),
                    recovered.authenticatedCsrfToken()))
        .switchIfEmpty(
            Mono.error(
                new IllegalArgumentException(
                    "Login OTP Verify protected replay recovery is unavailable")))
        .onErrorMap(
            exception ->
                exception instanceof IllegalArgumentException
                    ? exception
                    : new IllegalArgumentException(
                        "Login OTP Verify protected replay recovery failed"));
  }
}
