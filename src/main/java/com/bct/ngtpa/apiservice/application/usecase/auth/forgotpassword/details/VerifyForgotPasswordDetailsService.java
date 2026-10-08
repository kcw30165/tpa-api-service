package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordDetailsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowAttributesCommand;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class VerifyForgotPasswordDetailsService
    implements VerifyForgotPasswordDetailsUseCase {
  private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionMutationLockPort locks;
  private final ForgotPasswordProtectedAuthFlowContextLoader loader;
  private final ForgotPasswordDetailsLookupPort lookup;
  private final AuthFlowSessionLifecycleService lifecycleService;
  private final ForgotPasswordAuthFlowContextProtectorPort contextProtector;
  private final Clock clock;

  public VerifyForgotPasswordDetailsService(
      AuthFlowSessionPort sessions,
      AuthFlowSessionMutationLockPort locks,
      ForgotPasswordProtectedAuthFlowContextLoader loader,
      ForgotPasswordDetailsLookupPort lookup,
      AuthFlowSessionLifecycleService lifecycleService,
      ForgotPasswordAuthFlowContextProtectorPort contextProtector,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions);
    this.locks = Objects.requireNonNull(locks);
    this.loader = Objects.requireNonNull(loader);
    this.lookup = Objects.requireNonNull(lookup);
    this.lifecycleService = Objects.requireNonNull(lifecycleService);
    this.contextProtector = Objects.requireNonNull(contextProtector);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<ForgotPasswordDetailsResult> verify(ForgotPasswordDetailsCommand c) {
    if (c == null || c.sessionId() == null)
      return Mono.just(
          ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
    var validation = validator.validate(c.dateOfBirth(), c.email(), c.mobile());
    if (!validation.valid())
      return Mono.just(
          new ForgotPasswordDetailsResult(
              ForgotPasswordDetailsStatus.VALIDATION_FAILED, validation.targets()));
    Duration lease = c.lockLease() == null ? Duration.ofSeconds(10) : c.lockLease();
    return locks
        .acquire(c.sessionId(), lease)
        .flatMap(
            h ->
                h.acquired()
                    ? locked(c, h)
                    : Mono.just(
                        ForgotPasswordDetailsResult.of(
                            ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW)))
        .onErrorReturn(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
  }

  private Mono<ForgotPasswordDetailsResult> locked(
      ForgotPasswordDetailsCommand c, SessionLockHandle h) {
    Mono<ForgotPasswordDetailsResult> work =
        sessions
            .load(c.sessionId())
            .flatMap(
                optional -> {
                  if (optional.isEmpty())
                    return Mono.just(
                        ForgotPasswordDetailsResult.of(
                            ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                  var session = optional.get();
                  Instant now = Instant.now(clock);
                  if (session.isExpired(now)
                      || session.isTerminal()
                      || session.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                      || session.status() != AuthFlowStatus.IDENTITY_VERIFIED)
                    return Mono.just(
                        ForgotPasswordDetailsResult.of(
                            ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                  var load =
                      new ForgotPasswordProtectedAuthFlowContextCommand(
                          c.sessionId(),
                          c.csrfToken(),
                          c.origin(),
                          c.referer(),
                          c.requestId(),
                          Set.of(AuthFlowStatus.IDENTITY_VERIFIED),
                          true);
                  return loader
                      .load(load)
                      .flatMap(
                          result -> {
                            if (!result.isValid())
                              return Mono.just(
                                  ForgotPasswordDetailsResult.of(
                                      result.status()
                                                  == ForgotPasswordProtectedAuthFlowContextStatus
                                                      .CSRF_INVALID
                                              || result.status()
                                                  == ForgotPasswordProtectedAuthFlowContextStatus
                                                      .ORIGIN_REFERER_INVALID
                                          ? ForgotPasswordDetailsStatus.SECURITY_REJECTED
                                          : ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                            var context = result.context();
                            if (context.phase() != ForgotPasswordProtectedPhase.IDENTITY_VERIFIED)
                              return Mono.just(
                                  ForgotPasswordDetailsResult.of(
                                      ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                            return lookup
                                .verify(
                                    new ForgotPasswordDetailsLookupCommand(
                                        context.identityType(),
                                        context.identityValue(),
                                        c.dateOfBirth(),
                                        c.email(),
                                        c.mobile(),
                                        context.username(),
                                        c.requestId(),
                                        com.bct.ngtpa.apiservice.application.dto.ActorUserRole
                                            .ROLE_USER_MBR))
                                .flatMap(
                                    r -> {
                                      if (r.status() == ForgotPasswordDetailsLookupStatus.MATCHED) {
                                        // propagate account env into protected context if present
                                        String accountEnv = r.accountEnv();
                                        if (accountEnv == null || accountEnv.isBlank()) {
                                          return Mono.just(
                                              ForgotPasswordDetailsResult.of(
                                                  ForgotPasswordDetailsStatus.DETAILS_VERIFIED));
                                        }
                                        try {
                                          var newContext =
                                              new ForgotPasswordAuthFlowContext(
                                                  context.username(),
                                                  context.identityValue(),
                                                  context.identityType(),
                                                  context.identityVerified(),
                                                  context.resetSubjectId(),
                                                  context.contactChannel(),
                                                  context.protectedDestination(),
                                                  context.maskedDestination(),
                                                  context.resetAuthorization(),
                                                  accountEnv,
                                                  r.trustCode(),
                                                  r.schemeName(),
                                                  r.schemeNameChi(),
                                                  context.workflowVersion(),
                                                  context.phase());
                                          ForgotPasswordAuthFlowEncryptedContext encrypted =
                                              contextProtector.protect(newContext);
                                          Map<String, String> attributes =
                                              session.data() == null
                                                  ? new LinkedHashMap<>()
                                                  : new LinkedHashMap<>(
                                                      session.data().attributes());
                                          attributes.put(
                                              ForgotPasswordAuthFlowContextAttributeNames
                                                  .CONTEXT_CIPHERTEXT,
                                              encrypted.ciphertext());
                                          attributes.put(
                                              ForgotPasswordAuthFlowContextAttributeNames
                                                  .CONTEXT_KEY_VERSION,
                                              encrypted.keyVersion());
                                          attributes.put(
                                              ForgotPasswordAuthFlowContextAttributeNames
                                                  .CONTEXT_ALGORITHM,
                                              encrypted.algorithm());
                                          return lifecycleService
                                              .updateAttributes(
                                                  new UpdateAuthFlowAttributesCommand(
                                                      session.authFlowSessionId(),
                                                      Map.copyOf(attributes)))
                                              .map(
                                                  ignored ->
                                                      ForgotPasswordDetailsResult.of(
                                                          ForgotPasswordDetailsStatus
                                                              .DETAILS_VERIFIED))
                                              .onErrorReturn(
                                                  ForgotPasswordDetailsResult.of(
                                                      ForgotPasswordDetailsStatus.SYSTEM_FAILED));
                                        } catch (RuntimeException ex) {
                                          return Mono.just(
                                              ForgotPasswordDetailsResult.of(
                                                  ForgotPasswordDetailsStatus.SYSTEM_FAILED));
                                        }
                                      }
                                      return Mono.just(
                                          ForgotPasswordDetailsResult.of(
                                              ForgotPasswordDetailsStatus.BUSINESS_REJECTED));
                                    });
                          });
                })
            .onErrorReturn(
                ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
    return Mono.usingWhen(
        Mono.just(h),
        ignored -> work,
        ignored -> locks.release(h).onErrorReturn(false).then(),
        (ignored, error) -> locks.release(h).onErrorReturn(false).then(),
        ignored -> locks.release(h).onErrorReturn(false).then());
  }
}
